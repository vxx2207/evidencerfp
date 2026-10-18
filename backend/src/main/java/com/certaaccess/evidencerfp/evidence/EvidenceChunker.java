package com.certaaccess.evidencerfp.evidence;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministisches, abschnittsbewusstes Chunking. "Token" bezeichnet hier bewusst die lokale,
 * providerunabhängige Whitespace-Tokenisierung; dadurch bleiben Grenzen offline reproduzierbar.
 */
@Component
public class EvidenceChunker {
    public static final int MAX_TOKENS = 450;
    public static final int OVERLAP_TOKENS = 60;

    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Pattern HEADING = Pattern.compile(
            "^(?:#{1,6}\\s+|(?:\\d+(?:\\.\\d+)*)[.)]?\\s+)(.{2,120})$");

    public List<EvidenceChunk> chunk(String documentSha256,
                                     String documentName,
                                     String mediaType,
                                     String baseLocator,
                                     String text,
                                     LocalDate documentDate) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace('\0', ' ').replace("\r\n", "\n").replace('\r', '\n').strip();
        List<Section> sections = sections(normalized);
        List<EvidenceChunk> result = new ArrayList<>();
        for (Section section : sections) {
            appendSection(result, documentSha256, documentName, mediaType, baseLocator, section, documentDate);
        }
        return List.copyOf(result);
    }

    private void appendSection(List<EvidenceChunk> target,
                               String documentSha256,
                               String documentName,
                               String mediaType,
                               String baseLocator,
                               Section section,
                               LocalDate documentDate) {
        List<String> tokens = tokens(section.content());
        int start = 0;
        int part = 1;
        while (start < tokens.size()) {
            int hardEnd = Math.min(tokens.size(), start + MAX_TOKENS);
            int end = preferredSentenceBoundary(tokens, start, hardEnd);
            if (end <= start) end = hardEnd;
            String content = String.join(" ", tokens.subList(start, end)).strip();
            String locator = locator(baseLocator, section.heading(), part, tokens.size() > MAX_TOKENS);
            target.add(new EvidenceChunk(
                    EvidenceChunkIdentity.stableId(documentSha256, locator, content),
                    documentName, mediaType, locator, content, documentDate));
            if (end >= tokens.size()) break;
            int next = Math.max(start + 1, end - OVERLAP_TOKENS);
            start = preferredStartBoundary(tokens, next, end);
            part++;
        }
    }

    private int preferredSentenceBoundary(List<String> tokens, int start, int hardEnd) {
        if (hardEnd == tokens.size()) return hardEnd;
        int earliest = start + (MAX_TOKENS * 3 / 5);
        for (int index = hardEnd; index > earliest; index--) {
            if (endsSentence(tokens.get(index - 1))) return index;
        }
        return hardEnd;
    }

    private int preferredStartBoundary(List<String> tokens, int proposed, int previousEnd) {
        int latest = Math.min(previousEnd - 1, proposed + 12);
        for (int index = proposed; index <= latest; index++) {
            if (index == 0 || endsSentence(tokens.get(index - 1))) return index;
        }
        return proposed;
    }

    private boolean endsSentence(String token) {
        return token.endsWith(".") || token.endsWith("!") || token.endsWith("?")
                || token.endsWith(":");
    }

    private List<Section> sections(String text) {
        List<Section> result = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\\n")) {
            Matcher matcher = HEADING.matcher(line.strip());
            if (matcher.matches()) {
                addSection(result, heading, body);
                heading = matcher.group(1).replaceAll("\\s+", " ").strip();
                body = new StringBuilder();
            } else {
                if (!body.isEmpty()) body.append('\n');
                body.append(line);
            }
        }
        addSection(result, heading, body);
        return result.isEmpty() ? List.of(new Section(null, text)) : result;
    }

    private void addSection(List<Section> result, String heading, StringBuilder body) {
        String content = body.toString().replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n").strip();
        if (!content.isBlank()) result.add(new Section(heading, content));
    }

    private List<String> tokens(String content) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(content);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private String locator(String baseLocator, String heading, int part, boolean multiPart) {
        StringBuilder result = new StringBuilder(baseLocator == null || baseLocator.isBlank()
                ? "Dokument" : baseLocator.strip());
        if (heading != null && !heading.isBlank()) {
            String safeHeading = heading.length() <= 80 ? heading : heading.substring(0, 80).strip();
            result.append(" — ").append(safeHeading);
        }
        if (multiPart) result.append(", Abschnitt ").append(part);
        return result.toString();
    }

    private record Section(String heading, String content) { }
}
