package app.d6d.persistence.json;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small, dependency-free JSON codec.
 *
 * <p>The decoder returns {@code null}, {@link Boolean}, {@link String},
 * {@link Integer}, {@link Long}, {@link BigInteger}, {@link BigDecimal},
 * {@link List}, and insertion-ordered {@link Map} values. The encoder accepts
 * those values as well as other finite {@link Number} implementations,
 * {@link Character}, and both object and primitive arrays.</p>
 */
public final class Json {
    private static final BigInteger MIN_INTEGER = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX_INTEGER = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private Json() {
    }

    /** Encodes a supported Java value as JSON. */
    public static String encode(Object value) {
        StringBuilder result = new StringBuilder();
        new Encoder(result).write(value, "$", 0);
        return result.toString();
    }

    /** Alias for {@link #encode(Object)}. */
    public static String stringify(Object value) {
        return encode(value);
    }

    /** Alias for {@link #encode(Object)}. */
    public static String toJson(Object value) {
        return encode(value);
    }

    /** Decodes exactly one JSON value. */
    public static Object parse(String json) {
        return new Parser(Objects.requireNonNull(json, "json")).parseDocument();
    }

    /** Alias for {@link #parse(String)}. */
    public static Object decode(String json) {
        return parse(json);
    }

    /** Alias for {@link #parse(String)}. */
    public static Object fromJson(String json) {
        return parse(json);
    }

    /**
     * Decodes a JSON object, failing if the document contains another root
     * value.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?>)) {
            throw new JsonParseException("Expected an object at the document root", 0, 1, 1);
        }
        return (Map<String, Object>) value;
    }

    /** A syntax error with an exact source position. */
    public static final class JsonParseException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final int offset;
        private final int line;
        private final int column;

        private JsonParseException(String message, int offset, int line, int column) {
            super(message + " at line " + line + ", column " + column + " (offset " + offset + ")");
            this.offset = offset;
            this.line = line;
            this.column = column;
        }

        public int offset() {
            return offset;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }
    }

    private static final class Encoder {
        private static final int MAX_DEPTH = 512;
        private static final char[] HEX = "0123456789ABCDEF".toCharArray();

        private final StringBuilder output;
        private final IdentityHashMap<Object, String> containers = new IdentityHashMap<>();

        private Encoder(StringBuilder output) {
            this.output = output;
        }

        private void write(Object value, String path, int depth) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String text) {
                writeString(text, path);
            } else if (value instanceof Character character) {
                writeString(character.toString(), path);
            } else if (value instanceof Boolean bool) {
                output.append(bool);
            } else if (value instanceof Number number) {
                writeNumber(number, path);
            } else if (value instanceof Map<?, ?> map) {
                checkDepth(depth, path);
                writeMap(map, path, depth);
            } else if (value instanceof List<?> list) {
                checkDepth(depth, path);
                writeList(list, path, depth);
            } else if (value.getClass().isArray()) {
                checkDepth(depth, path);
                writeArray(value, path, depth);
            } else {
                throw encodingError(path, "unsupported value type " + value.getClass().getName());
            }
        }

        private void writeMap(Map<?, ?> map, String path, int depth) {
            enter(map, path);
            try {
                output.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        String type = entry.getKey() == null
                                ? "null"
                                : entry.getKey().getClass().getName();
                        throw encodingError(path, "object key must be a string, but was " + type);
                    }
                    if (!first) {
                        output.append(',');
                    }
                    first = false;
                    writeString(key, path);
                    output.append(':');
                    write(entry.getValue(), memberPath(path, key), depth + 1);
                }
                output.append('}');
            } finally {
                leave(map);
            }
        }

        private void writeList(List<?> list, String path, int depth) {
            enter(list, path);
            try {
                output.append('[');
                for (int index = 0; index < list.size(); index++) {
                    if (index > 0) {
                        output.append(',');
                    }
                    write(list.get(index), path + '[' + index + ']', depth + 1);
                }
                output.append(']');
            } finally {
                leave(list);
            }
        }

        private void writeArray(Object array, String path, int depth) {
            enter(array, path);
            try {
                output.append('[');
                int length = Array.getLength(array);
                for (int index = 0; index < length; index++) {
                    if (index > 0) {
                        output.append(',');
                    }
                    write(Array.get(array, index), path + '[' + index + ']', depth + 1);
                }
                output.append(']');
            } finally {
                leave(array);
            }
        }

        private void enter(Object container, String path) {
            String previousPath = containers.put(container, path);
            if (previousPath != null) {
                containers.put(container, previousPath);
                throw encodingError(path, "cyclic reference to " + previousPath);
            }
        }

        private void leave(Object container) {
            containers.remove(container);
        }

        private void writeNumber(Number number, String path) {
            if (number instanceof Double value && !Double.isFinite(value)) {
                throw encodingError(path, "JSON does not support non-finite number " + value);
            }
            if (number instanceof Float value && !Float.isFinite(value)) {
                throw encodingError(path, "JSON does not support non-finite number " + value);
            }

            String text = number.toString();
            if (!isJsonNumber(text)) {
                throw encodingError(path, "number has an invalid JSON representation: " + text);
            }
            output.append(text);
        }

        private void writeString(String text, String path) {
            output.append('"');
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                switch (character) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\f' -> output.append("\\f");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    case '\t' -> output.append("\\t");
                    default -> {
                        if (Character.isHighSurrogate(character)) {
                            if (index + 1 >= text.length()
                                    || !Character.isLowSurrogate(text.charAt(index + 1))) {
                                throw encodingError(path, "string contains an unpaired high surrogate");
                            }
                            appendUnicodeEscape(character);
                            appendUnicodeEscape(text.charAt(++index));
                        } else if (Character.isLowSurrogate(character)) {
                            throw encodingError(path, "string contains an unpaired low surrogate");
                        } else if (character < 0x20 || character > 0x7e) {
                            appendUnicodeEscape(character);
                        } else {
                            output.append(character);
                        }
                    }
                }
            }
            output.append('"');
        }

        private void appendUnicodeEscape(char character) {
            output.append("\\u")
                    .append(HEX[(character >>> 12) & 0xf])
                    .append(HEX[(character >>> 8) & 0xf])
                    .append(HEX[(character >>> 4) & 0xf])
                    .append(HEX[character & 0xf]);
        }

        private static void checkDepth(int depth, String path) {
            if (depth >= MAX_DEPTH) {
                throw encodingError(path, "maximum nesting depth of " + MAX_DEPTH + " exceeded");
            }
        }

        private static IllegalArgumentException encodingError(String path, String detail) {
            return new IllegalArgumentException("Cannot encode JSON value at " + path + ": " + detail);
        }

        private static String memberPath(String parent, String key) {
            if (isSimpleIdentifier(key)) {
                return parent + '.' + key;
            }
            return parent + "[\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
        }

        private static boolean isSimpleIdentifier(String text) {
            if (text.isEmpty() || !(Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
                return false;
            }
            for (int index = 1; index < text.length(); index++) {
                char character = text.charAt(index);
                if (!(Character.isLetterOrDigit(character) || character == '_')) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 512;

        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Object parseDocument() {
            skipWhitespace();
            if (atEnd()) {
                throw error("Expected a JSON value");
            }
            Object value = parseValue(0);
            skipWhitespace();
            if (!atEnd()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (atEnd()) {
                throw error("Expected a JSON value");
            }
            char character = peek();
            return switch (character) {
                case 'n' -> parseLiteral("null", null);
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case '"' -> parseString();
                case '{' -> {
                    checkDepth(depth);
                    yield parseObject(depth);
                }
                case '[' -> {
                    checkDepth(depth);
                    yield parseArray(depth);
                }
                default -> {
                    if (character == '-' || isDigit(character)) {
                        yield parseNumber();
                    }
                    throw error("Unexpected character " + describe(character) + "; expected a JSON value");
                }
            };
        }

        private Object parseLiteral(String literal, Object value) {
            int start = index;
            for (int offset = 0; offset < literal.length(); offset++) {
                if (atEnd() || source.charAt(index) != literal.charAt(offset)) {
                    index = start;
                    throw error("Expected '" + literal + "'");
                }
                index++;
            }
            return value;
        }

        private Map<String, Object> parseObject(int depth) {
            index++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }

            while (true) {
                if (atEnd() || peek() != '"') {
                    throw error("Expected a quoted object key");
                }
                int keyOffset = index;
                String key = parseString();
                skipWhitespace();
                expect(':', "Expected ':' after object key");
                skipWhitespace();
                Object value = parseValue(depth + 1);
                if (result.containsKey(key)) {
                    throw errorAt(keyOffset, "Duplicate object key " + quoteForMessage(key));
                }
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',', "Expected ',' or '}' after object member");
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            index++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }

            while (true) {
                result.add(parseValue(depth + 1));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',', "Expected ',' or ']' after array element");
                skipWhitespace();
            }
        }

        private String parseString() {
            index++;
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char character = source.charAt(index++);
                if (character == '"') {
                    return result.toString();
                }
                if (character == '\\') {
                    parseEscape(result);
                } else if (character < 0x20) {
                    throw errorAt(index - 1, "Unescaped control character in string");
                } else if (Character.isHighSurrogate(character)) {
                    if (atEnd() || !Character.isLowSurrogate(peek())) {
                        throw errorAt(index - 1, "Unpaired high surrogate in string");
                    }
                    result.append(character).append(source.charAt(index++));
                } else if (Character.isLowSurrogate(character)) {
                    throw errorAt(index - 1, "Unpaired low surrogate in string");
                } else {
                    result.append(character);
                }
            }
            throw error("Unterminated string");
        }

        private void parseEscape(StringBuilder result) {
            if (atEnd()) {
                throw error("Unterminated escape sequence");
            }
            int escapeOffset = index - 1;
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> appendEscapedUnicode(result, escapeOffset);
                default -> throw errorAt(escapeOffset,
                        "Invalid escape sequence '\\" + escaped + "'");
            }
        }

        private void appendEscapedUnicode(StringBuilder result, int escapeOffset) {
            char first = readHexCodeUnit();
            if (Character.isHighSurrogate(first)) {
                if (index + 1 >= source.length()
                        || source.charAt(index) != '\\'
                        || source.charAt(index + 1) != 'u') {
                    throw errorAt(escapeOffset, "High surrogate must be followed by a low-surrogate Unicode escape");
                }
                index += 2;
                char second = readHexCodeUnit();
                if (!Character.isLowSurrogate(second)) {
                    throw errorAt(escapeOffset, "High surrogate must be followed by a low-surrogate Unicode escape");
                }
                result.append(first).append(second);
            } else if (Character.isLowSurrogate(first)) {
                throw errorAt(escapeOffset, "Unexpected low-surrogate Unicode escape");
            } else {
                result.append(first);
            }
        }

        private char readHexCodeUnit() {
            if (source.length() - index < 4) {
                throw error("Incomplete Unicode escape; expected four hexadecimal digits");
            }
            int value = 0;
            for (int count = 0; count < 4; count++) {
                char digit = source.charAt(index++);
                int hexadecimal = Character.digit(digit, 16);
                if (hexadecimal < 0) {
                    throw errorAt(index - 1,
                            "Invalid hexadecimal digit " + describe(digit) + " in Unicode escape");
                }
                value = (value << 4) | hexadecimal;
            }
            return (char) value;
        }

        private Number parseNumber() {
            int start = index;
            consume('-');

            if (consume('0')) {
                if (!atEnd() && isDigit(peek())) {
                    throw error("Leading zero is not allowed in a JSON number");
                }
            } else {
                requireDigit("Expected a digit in number");
                while (!atEnd() && isDigit(peek())) {
                    index++;
                }
            }

            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                requireDigit("Expected at least one digit after decimal point");
                while (!atEnd() && isDigit(peek())) {
                    index++;
                }
            }

            if (consume('e') || consume('E')) {
                decimal = true;
                if (!atEnd() && (peek() == '+' || peek() == '-')) {
                    index++;
                }
                requireDigit("Expected at least one digit in exponent");
                while (!atEnd() && isDigit(peek())) {
                    index++;
                }
            }

            String token = source.substring(start, index);
            try {
                if (decimal) {
                    return new BigDecimal(token);
                }
                BigInteger integer = new BigInteger(token);
                if (integer.compareTo(MIN_INTEGER) >= 0 && integer.compareTo(MAX_INTEGER) <= 0) {
                    return integer.intValue();
                }
                if (integer.compareTo(MIN_LONG) >= 0 && integer.compareTo(MAX_LONG) <= 0) {
                    return integer.longValue();
                }
                return integer;
            } catch (NumberFormatException exception) {
                throw errorAt(start, "Invalid or unsupported JSON number " + token);
            }
        }

        private void requireDigit(String message) {
            if (atEnd() || !isDigit(peek())) {
                throw error(message);
            }
            index++;
        }

        private void expect(char expected, String message) {
            if (!consume(expected)) {
                throw error(message);
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && peek() == expected) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() {
            return source.charAt(index);
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char character = peek();
                if (character == ' ' || character == '\t' || character == '\r' || character == '\n') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private void checkDepth(int depth) {
            if (depth >= MAX_DEPTH) {
                throw error("Maximum nesting depth of " + MAX_DEPTH + " exceeded");
            }
        }

        private JsonParseException error(String message) {
            return errorAt(index, message);
        }

        private JsonParseException errorAt(int offset, String message) {
            int line = 1;
            int column = 1;
            for (int cursor = 0; cursor < offset && cursor < source.length(); cursor++) {
                char character = source.charAt(cursor);
                if (character == '\r') {
                    line++;
                    column = 1;
                    if (cursor + 1 < offset && source.charAt(cursor + 1) == '\n') {
                        cursor++;
                    }
                } else if (character == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new JsonParseException(message, offset, line, column);
        }

        private static String quoteForMessage(String text) {
            return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }

    private static boolean isJsonNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int cursor = 0;
        if (text.charAt(cursor) == '-') {
            cursor++;
            if (cursor == text.length()) {
                return false;
            }
        }

        if (text.charAt(cursor) == '0') {
            cursor++;
        } else if (text.charAt(cursor) >= '1' && text.charAt(cursor) <= '9') {
            do {
                cursor++;
            } while (cursor < text.length() && isDigit(text.charAt(cursor)));
        } else {
            return false;
        }

        if (cursor < text.length() && text.charAt(cursor) == '.') {
            cursor++;
            int fractionStart = cursor;
            while (cursor < text.length() && isDigit(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor == fractionStart) {
                return false;
            }
        }

        if (cursor < text.length() && (text.charAt(cursor) == 'e' || text.charAt(cursor) == 'E')) {
            cursor++;
            if (cursor < text.length() && (text.charAt(cursor) == '+' || text.charAt(cursor) == '-')) {
                cursor++;
            }
            int exponentStart = cursor;
            while (cursor < text.length() && isDigit(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor == exponentStart) {
                return false;
            }
        }
        return cursor == text.length();
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static String describe(char character) {
        if (character < 0x20 || character == 0x7f) {
            return String.format("U+%04X", (int) character);
        }
        return "'" + character + "'";
    }
}
