package com.certaaccess.evidencerfp.evidence;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Request-unabhängige Kopie eines validierten Uploads. Servlet-Multipart-Dateien dürfen nach
 * Abschluss des HTTP-Requests nicht von einem Hintergrundjob weiterverwendet werden.
 */
public final class BufferedMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    private BufferedMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content;
    }

    public static BufferedMultipartFile copyOf(MultipartFile source) {
        try {
            return new BufferedMultipartFile(source.getName(), source.getOriginalFilename(),
                    source.getContentType(), source.getBytes());
        } catch (IOException exception) {
            throw new InvalidUploadException("Upload konnte nicht für die Analyse gepuffert werden.");
        }
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override public byte[] getBytes() { return content.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), content);
    }
}
