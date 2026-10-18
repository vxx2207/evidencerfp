package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUploadKind;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Speichert validierte Request-Uploads außerhalb des Servlet-Lebenszyklus. In der Datenbank
 * landet nur ein relativer, serverseitig erzeugter Pfad; vom Client gelieferte Dateinamen
 * werden niemals als Dateisystempfad verwendet.
 */
@Component
public class AnalysisUploadStorage {
    private static final int BUFFER_SIZE = 16 * 1024;
    private final Path root;

    public AnalysisUploadStorage(@Value("${evidence-rfp.analysis.storage-directory:"
            + "${java.io.tmpdir}/evidence-rfp/jobs}") String storageDirectory) {
        this.root = Path.of(storageDirectory).toAbsolutePath().normalize();
        createPrivateDirectory(root);
    }

    public List<AnalysisUpload> store(UUID analysisId, MultipartFile rfp,
                                      List<MultipartFile> evidence, MultipartFile iamSnapshot) {
        Path directory = analysisDirectory(analysisId);
        createPrivateDirectory(directory);
        List<AnalysisUpload> uploads = new ArrayList<>();
        try {
            uploads.add(storeOne(analysisId, AnalysisUploadKind.RFP, 0, rfp));
            for (int index = 0; index < evidence.size(); index++) {
                uploads.add(storeOne(analysisId, AnalysisUploadKind.EVIDENCE, index, evidence.get(index)));
            }
            if (iamSnapshot != null && !iamSnapshot.isEmpty()) {
                uploads.add(storeOne(analysisId, AnalysisUploadKind.IAM_SNAPSHOT, 0, iamSnapshot));
            }
            return List.copyOf(uploads);
        } catch (RuntimeException exception) {
            delete(analysisId);
            throw exception;
        }
    }

    public MultipartFile open(AnalysisUpload upload) {
        Path path = resolve(upload.storagePath());
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Persistierter Analyse-Upload fehlt.");
        }
        verify(path, upload);
        String partName = switch (upload.kind()) {
            case RFP -> "rfp";
            case EVIDENCE -> "evidence";
            case IAM_SNAPSHOT -> "iamSnapshot";
        };
        return new PathMultipartFile(partName, upload.originalFilename(), upload.mediaType(), path,
                upload.sizeBytes());
    }

    public void delete(UUID analysisId) {
        Path directory = analysisDirectory(analysisId);
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new StorageCleanupException(exception);
                }
            });
        } catch (IOException | StorageCleanupException exception) {
            throw new IllegalStateException("Temporäre Analysedateien konnten nicht entfernt werden.", exception);
        }
    }

    public String requestHash(String customerName, List<AnalysisUpload> uploads) {
        MessageDigest digest = sha256();
        update(digest, customerName == null ? "" : customerName);
        uploads.stream()
                .sorted(Comparator.comparing(AnalysisUpload::kind).thenComparingInt(AnalysisUpload::position))
                .forEach(upload -> {
                    update(digest, upload.kind().name());
                    update(digest, Integer.toString(upload.position()));
                    update(digest, upload.sha256());
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private AnalysisUpload storeOne(UUID analysisId, AnalysisUploadKind kind, int position,
                                    MultipartFile source) {
        UUID id = UUID.randomUUID();
        String relative = analysisId + "/" + id + ".upload";
        Path destination = resolve(relative);
        MessageDigest digest = sha256();
        long bytes = 0;
        try (InputStream input = new DigestInputStream(source.getInputStream(), digest);
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                output.write(buffer, 0, read);
                bytes += read;
            }
            restrictFile(destination);
        } catch (IOException exception) {
            throw new InvalidUploadException("Upload konnte nicht sicher zwischengespeichert werden.");
        }
        String originalFilename = safeOriginalFilename(source.getOriginalFilename());
        return new AnalysisUpload(id, analysisId, kind, position, originalFilename,
                source.getContentType(), bytes, HexFormat.of().formatHex(digest.digest()), relative);
    }

    private void verify(Path path, AnalysisUpload upload) {
        try {
            if (Files.size(path) != upload.sizeBytes()) throw new IllegalStateException();
            MessageDigest digest = sha256();
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(upload.sha256()), digest.digest())) {
                throw new IllegalStateException();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Persistierter Analyse-Upload ist beschädigt.", exception);
        }
    }

    private Path analysisDirectory(UUID analysisId) {
        Path path = root.resolve(analysisId.toString()).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalStateException("Ungültiger Speicherpfad.");
        return path;
    }

    private Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalStateException("Ungültiger Speicherpfad.");
        return path;
    }

    private void createPrivateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            try {
                Files.setPosixFilePermissions(directory, EnumSet.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            } catch (UnsupportedOperationException ignored) {
                // Windows verwendet ACLs; die Anwendung legt keine Freigabe für dieses Verzeichnis an.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Lokaler Analysespeicher konnte nicht angelegt werden.", exception);
        }
    }

    private void restrictFile(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Siehe createPrivateDirectory.
        }
    }

    private String safeOriginalFilename(String value) {
        if (value == null || value.isBlank()) return "upload";
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (name.isBlank()) return "upload";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static final class StorageCleanupException extends RuntimeException {
        private StorageCleanupException(IOException cause) { super(cause); }
    }

    private record PathMultipartFile(String name, String originalFilename, String contentType,
                                     Path path, long size) implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return size == 0; }
        @Override public long getSize() { return size; }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        @Override public void transferTo(File destination) throws IOException {
            Files.copy(path, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
