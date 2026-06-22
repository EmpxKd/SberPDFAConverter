package by.eshed.pdfa.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Минимальный парсер multipart/form-data на чистом JDK (без commons-fileupload — лишняя
 * зависимость не нужна стеку, см. DECISIONS.md о минимальном наборе библиотек). REST-эндпойнт
 * конвертации принимает несколько файлов страниц + опциональный файл подписи + текстовые поля
 * метаданных одним запросом, поэтому multipart неизбежен.
 */
public final class MultipartParser {

    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]*)\"");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]*)\"");

    private MultipartParser() {
    }

    public static final class Part {
        private final String name;
        private final String filename;
        private final String contentType;
        private final byte[] data;

        Part(String name, String filename, String contentType, byte[] data) {
            this.name = name;
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }

        public String name() {
            return name;
        }

        public String filename() {
            return filename;
        }

        public String contentType() {
            return contentType;
        }

        public byte[] data() {
            return data;
        }

        public boolean isFile() {
            return filename != null && !filename.isBlank();
        }
    }

    public static List<Part> parse(byte[] body, String boundary) {
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Integer> positions = findAll(body, delimiter);
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i + 1 < positions.size(); i++) {
            int afterDelimiter = positions.get(i) + delimiter.length;
            if (startsWith(body, afterDelimiter, "--")) {
                break; // финальный маркер "--boundary--"
            }
            int start = skipLeadingCrlf(body, afterDelimiter);
            int end = trimTrailingCrlf(body, positions.get(i + 1));
            if (start >= end) {
                continue;
            }
            parts.add(parsePart(Arrays.copyOfRange(body, start, end)));
        }
        return parts;
    }

    private static Part parsePart(byte[] partBytes) {
        int headerEnd = indexOf(partBytes, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
        if (headerEnd < 0) {
            return new Part(null, null, null, new byte[0]);
        }
        String headerText = new String(partBytes, 0, headerEnd, StandardCharsets.UTF_8);
        byte[] content = Arrays.copyOfRange(partBytes, headerEnd + 4, partBytes.length);

        String name = null;
        String filename = null;
        String contentType = null;
        for (String line : headerText.split("\r\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-disposition:")) {
                Matcher nameMatcher = NAME_PATTERN.matcher(line);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1);
                }
                Matcher filenameMatcher = FILENAME_PATTERN.matcher(line);
                if (filenameMatcher.find()) {
                    filename = filenameMatcher.group(1);
                }
            } else if (lower.startsWith("content-type:")) {
                contentType = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return new Part(name, filename, contentType, content);
    }

    private static int skipLeadingCrlf(byte[] data, int from) {
        if (from + 1 < data.length && data[from] == '\r' && data[from + 1] == '\n') {
            return from + 2;
        }
        return from;
    }

    private static int trimTrailingCrlf(byte[] data, int boundaryStart) {
        int end = boundaryStart;
        if (end >= 2 && data[end - 2] == '\r' && data[end - 1] == '\n') {
            end -= 2;
        }
        return end;
    }

    private static boolean startsWith(byte[] data, int offset, String prefix) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.ISO_8859_1);
        if (offset + prefixBytes.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefixBytes.length; i++) {
            if (data[offset + i] != prefixBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> findAll(byte[] data, byte[] pattern) {
        List<Integer> positions = new ArrayList<>();
        int from = 0;
        while (true) {
            int idx = indexOf(data, pattern, from);
            if (idx < 0) {
                break;
            }
            positions.add(idx);
            from = idx + pattern.length;
        }
        return positions;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
