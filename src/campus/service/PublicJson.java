package campus.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads public JSON responses using only the Java runtime. */
public final class PublicJson {
    private static final int MAX_BYTES = 262_144;
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    private PublicJson() { }

    public static String fetch(URI uri) throws IOException {
        var connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            try (var stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("JSON response too large");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static Object parse(String json) throws IOException {
        Reader reader = new Reader(json);
        try {
            Object value = reader.read(0);
            reader.skipSpace();
            if (reader.index != json.length()) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw new IOException("Invalid JSON", ex);
        }
    }

    private static final class Reader {
        private final String text;
        private int index;

        Reader(String text) { this.text = text; }

        private void skipSpace() {
            while (index < text.length() && " \r\n\t".indexOf(text.charAt(index)) >= 0) index++;
        }

        private boolean take(char c) {
            skipSpace();
            if (index == text.length() || text.charAt(index) != c) return false;
            index++;
            return true;
        }

        private void expect(char c) {
            if (!take(c)) throw new IllegalArgumentException();
        }

        private Object read(int depth) {
            if (depth > 32) throw new IllegalArgumentException();
            if (take('{')) {
                Map<String, Object> map = new LinkedHashMap<>();
                if (take('}')) return map;
                do {
                    expect('"');
                    String key = string();
                    expect(':');
                    map.put(key, read(depth + 1));
                } while (take(','));
                expect('}');
                return map;
            }
            if (take('[')) {
                List<Object> list = new ArrayList<>();
                if (take(']')) return list;
                do { list.add(read(depth + 1)); } while (take(','));
                expect(']');
                return list;
            }
            if (take('"')) return string();
            if (literal("true")) return true;
            if (literal("false")) return false;
            if (literal("null")) return null;
            var number = NUMBER.matcher(text).region(index, text.length());
            if (!number.lookingAt()) throw new IllegalArgumentException();
            index = number.end();
            return Double.valueOf(number.group());
        }

        private boolean literal(String value) {
            if (!text.startsWith(value, index)) return false;
            index += value.length();
            return true;
        }

        private String string() {
            StringBuilder value = new StringBuilder();
            while (true) {
                char c = text.charAt(index++);
                if (c == '"') return value.toString();
                if (c < 32) throw new IllegalArgumentException();
                if (c == '\\') {
                    char escape = text.charAt(index++);
                    c = switch (escape) {
                        case '"', '\\', '/' -> escape;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> {
                            char decoded = (char) Integer.parseInt(text.substring(index, index + 4), 16);
                            index += 4;
                            yield decoded;
                        }
                        default -> throw new IllegalArgumentException();
                    };
                }
                value.append(c);
            }
        }
    }
}
