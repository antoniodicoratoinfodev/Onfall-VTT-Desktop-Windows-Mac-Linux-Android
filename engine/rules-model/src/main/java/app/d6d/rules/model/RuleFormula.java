package app.d6d.rules.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Formula numerica sicura e deterministica.
 *
 * <p>I riferimenti a valori usano {@code ${entity:id}}; le tabelle si leggono
 * con {@code lookup("table:id", chiave)}. Non esistono reflection, I/O, rete o
 * codice importato. Parser e valutatore hanno budget rigidi.</p>
 */
public final class RuleFormula {
    public static final int MAX_SOURCE_LENGTH = 4_096;
    private static final int MAX_TOKENS = 1_024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_EVALUATION_STEPS = 8_192;
    private static final MathContext MATH = MathContext.DECIMAL128;

    public interface Context {
        BigDecimal value(String id);

        BigDecimal lookup(String tableId, BigDecimal key);
    }

    private final String source;
    private final Node root;
    private final Set<String> valueReferences;
    private final Set<String> tableReferences;

    private RuleFormula(String source, Node root, Set<String> valueReferences, Set<String> tableReferences) {
        this.source = source;
        this.root = root;
        this.valueReferences = Set.copyOf(valueReferences);
        this.tableReferences = Set.copyOf(tableReferences);
    }

    public static RuleFormula compile(String source) {
        String normalized = Objects.requireNonNull(source, "source").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Formula cannot be blank");
        if (normalized.length() > MAX_SOURCE_LENGTH) throw new IllegalArgumentException("Formula is too long");
        Parser parser = new Parser(normalized);
        Node root = parser.parse();
        return new RuleFormula(normalized, root, parser.valueReferences, parser.tableReferences);
    }

    public static RuleFormula constant(long value) {
        return compile(Long.toString(value));
    }

    public String source() {
        return source;
    }

    public Set<String> valueReferences() {
        return valueReferences;
    }

    public Set<String> tableReferences() {
        return tableReferences;
    }

    public BigDecimal evaluate(Context context) {
        Objects.requireNonNull(context, "context");
        return normalize(root.evaluate(context, new Budget()));
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null) throw new IllegalStateException("Formula produced null");
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : new BigDecimal(normalized.toPlainString());
    }

    private interface Node {
        BigDecimal evaluate(Context context, Budget budget);
    }

    private record NumberNode(BigDecimal value) implements Node {
        @Override public BigDecimal evaluate(Context context, Budget budget) {
            budget.step();
            return value;
        }
    }

    private record ValueNode(String id) implements Node {
        @Override public BigDecimal evaluate(Context context, Budget budget) {
            budget.step();
            BigDecimal result = context.value(id);
            if (result == null) throw new IllegalArgumentException("Missing formula value " + id);
            return result;
        }
    }

    private record UnaryNode(String operator, Node operand) implements Node {
        @Override public BigDecimal evaluate(Context context, Budget budget) {
            budget.step();
            BigDecimal value = operand.evaluate(context, budget);
            return switch (operator) {
                case "+" -> value;
                case "-" -> value.negate(MATH);
                case "!" -> truth(value) ? BigDecimal.ZERO : BigDecimal.ONE;
                default -> throw new IllegalStateException("Unknown unary operator " + operator);
            };
        }
    }

    private record BinaryNode(String operator, Node left, Node right) implements Node {
        @Override public BigDecimal evaluate(Context context, Budget budget) {
            budget.step();
            BigDecimal first = left.evaluate(context, budget);
            if (operator.equals("&&") && !truth(first)) return BigDecimal.ZERO;
            if (operator.equals("||") && truth(first)) return BigDecimal.ONE;
            BigDecimal second = right.evaluate(context, budget);
            return switch (operator) {
                case "+" -> first.add(second, MATH);
                case "-" -> first.subtract(second, MATH);
                case "*" -> first.multiply(second, MATH);
                case "/" -> {
                    if (second.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("Division by zero in rule formula");
                    }
                    yield first.divide(second, MATH);
                }
                case "%" -> {
                    if (second.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("Division by zero in rule formula");
                    }
                    yield first.remainder(second, MATH);
                }
                case "<" -> bool(first.compareTo(second) < 0);
                case "<=" -> bool(first.compareTo(second) <= 0);
                case ">" -> bool(first.compareTo(second) > 0);
                case ">=" -> bool(first.compareTo(second) >= 0);
                case "==" -> bool(first.compareTo(second) == 0);
                case "!=" -> bool(first.compareTo(second) != 0);
                case "&&" -> bool(truth(second));
                case "||" -> bool(truth(second));
                default -> throw new IllegalStateException("Unknown binary operator " + operator);
            };
        }
    }

    private record FunctionNode(String name, List<Node> arguments, String textArgument) implements Node {
        @Override public BigDecimal evaluate(Context context, Budget budget) {
            budget.step();
            if (name.equals("if")) {
                requireArity(3);
                return truth(arguments.get(0).evaluate(context, budget))
                        ? arguments.get(1).evaluate(context, budget)
                        : arguments.get(2).evaluate(context, budget);
            }
            if (name.equals("lookup")) {
                requireArity(1);
                return context.lookup(textArgument, arguments.get(0).evaluate(context, budget));
            }
            List<BigDecimal> values = arguments.stream().map(node -> node.evaluate(context, budget)).toList();
            return switch (name) {
                case "min" -> {
                    requireAtLeast(values, 1);
                    yield values.stream().min(BigDecimal::compareTo).orElseThrow();
                }
                case "max" -> {
                    requireAtLeast(values, 1);
                    yield values.stream().max(BigDecimal::compareTo).orElseThrow();
                }
                case "clamp" -> {
                    requireArity(3);
                    BigDecimal minimum = values.get(1);
                    BigDecimal maximum = values.get(2);
                    if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("clamp minimum exceeds maximum");
                    yield values.get(0).max(minimum).min(maximum);
                }
                case "abs" -> { requireArity(1); yield values.get(0).abs(MATH); }
                case "floor" -> { requireArity(1); yield values.get(0).setScale(0, RoundingMode.FLOOR); }
                case "ceil" -> { requireArity(1); yield values.get(0).setScale(0, RoundingMode.CEILING); }
                case "round" -> { requireArity(1); yield values.get(0).setScale(0, RoundingMode.HALF_UP); }
                default -> throw new IllegalStateException("Unknown formula function " + name);
            };
        }

        private void requireArity(int expected) {
            if (arguments.size() != expected) {
                throw new IllegalArgumentException(name + " expects " + expected + " arguments");
            }
        }

        private static void requireAtLeast(List<BigDecimal> values, int minimum) {
            if (values.size() < minimum) throw new IllegalArgumentException("Function has too few arguments");
        }
    }

    private static boolean truth(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) != 0;
    }

    private static BigDecimal bool(boolean value) {
        return value ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private static final class Budget {
        private int remaining = MAX_EVALUATION_STEPS;
        void step() {
            if (--remaining < 0) throw new IllegalStateException("Rule formula evaluation budget exceeded");
        }
    }

    private enum TokenKind { NUMBER, IDENTIFIER, VARIABLE, STRING, OPERATOR, LEFT, RIGHT, COMMA, END }

    private record Token(TokenKind kind, String text, int offset) { }

    private static final class Lexer {
        private final String source;
        private int cursor;
        private int emitted;

        private Lexer(String source) {
            this.source = source;
        }

        private Token next() {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (++emitted > MAX_TOKENS) throw error("Formula contains too many tokens", cursor);
            if (cursor >= source.length()) return new Token(TokenKind.END, "", cursor);
            int start = cursor;
            char current = source.charAt(cursor);
            if (current == '$' && cursor + 1 < source.length() && source.charAt(cursor + 1) == '{') {
                cursor += 2;
                int contentStart = cursor;
                while (cursor < source.length() && source.charAt(cursor) != '}') cursor++;
                if (cursor >= source.length()) throw error("Unclosed formula reference", start);
                String id = source.substring(contentStart, cursor).trim();
                cursor++;
                if (id.isEmpty()) throw error("Formula reference cannot be blank", start);
                return new Token(TokenKind.VARIABLE, id, start);
            }
            if (Character.isDigit(current) || (current == '.' && cursor + 1 < source.length()
                    && Character.isDigit(source.charAt(cursor + 1)))) {
                boolean dot = false;
                while (cursor < source.length()) {
                    char candidate = source.charAt(cursor);
                    if (candidate == '.') {
                        if (dot) break;
                        dot = true;
                    } else if (!Character.isDigit(candidate)) break;
                    cursor++;
                }
                return new Token(TokenKind.NUMBER, source.substring(start, cursor), start);
            }
            if (Character.isLetter(current) || current == '_') {
                cursor++;
                while (cursor < source.length()) {
                    char candidate = source.charAt(cursor);
                    if (!Character.isLetterOrDigit(candidate) && candidate != '_' && candidate != '.'
                            && candidate != ':' && candidate != '-') break;
                    cursor++;
                }
                return new Token(TokenKind.IDENTIFIER, source.substring(start, cursor), start);
            }
            if (current == '"' || current == '\'') {
                char quote = current;
                cursor++;
                StringBuilder value = new StringBuilder();
                while (cursor < source.length() && source.charAt(cursor) != quote) {
                    char candidate = source.charAt(cursor++);
                    if (candidate == '\\') {
                        if (cursor >= source.length()) throw error("Unclosed string escape", start);
                        char escaped = source.charAt(cursor++);
                        if (escaped != quote && escaped != '\\') throw error("Unsupported string escape", cursor - 2);
                        candidate = escaped;
                    }
                    value.append(candidate);
                }
                if (cursor >= source.length()) throw error("Unclosed string literal", start);
                cursor++;
                return new Token(TokenKind.STRING, value.toString(), start);
            }
            cursor++;
            return switch (current) {
                case '(' -> new Token(TokenKind.LEFT, "(", start);
                case ')' -> new Token(TokenKind.RIGHT, ")", start);
                case ',' -> new Token(TokenKind.COMMA, ",", start);
                case '+', '-', '*', '/', '%' -> new Token(TokenKind.OPERATOR, Character.toString(current), start);
                case '<', '>', '=', '!', '&', '|' -> {
                    String operator = Character.toString(current);
                    if (cursor < source.length()) {
                        char following = source.charAt(cursor);
                        if (following == '=' || (following == current && (current == '&' || current == '|'))) {
                            operator += following;
                            cursor++;
                        }
                    }
                    if (operator.equals("=") || operator.equals("&") || operator.equals("|")) {
                        throw error("Unsupported operator " + operator, start);
                    }
                    yield new Token(TokenKind.OPERATOR, operator, start);
                }
                default -> throw error("Unexpected character '" + current + "'", start);
            };
        }

        private IllegalArgumentException error(String message, int offset) {
            return new IllegalArgumentException(message + " at offset " + offset);
        }
    }

    private static final class Parser {
        private final Lexer lexer;
        private Token current;
        private int depth;
        private final Set<String> valueReferences = new LinkedHashSet<>();
        private final Set<String> tableReferences = new LinkedHashSet<>();

        private Parser(String source) {
            lexer = new Lexer(source);
            current = lexer.next();
        }

        private Node parse() {
            Node result = expression(0);
            if (current.kind != TokenKind.END) throw error("Unexpected token " + current.text);
            return result;
        }

        private Node expression(int minimumPrecedence) {
            if (++depth > MAX_DEPTH) throw error("Formula nesting is too deep");
            Node left = unary();
            while (current.kind == TokenKind.OPERATOR) {
                int precedence = precedence(current.text);
                if (precedence < minimumPrecedence) break;
                String operator = current.text;
                advance();
                Node right = expression(precedence + 1);
                left = new BinaryNode(operator, left, right);
            }
            depth--;
            return left;
        }

        private Node unary() {
            if (current.kind == TokenKind.OPERATOR
                    && (current.text.equals("+") || current.text.equals("-") || current.text.equals("!"))) {
                String operator = current.text;
                advance();
                return new UnaryNode(operator, unary());
            }
            return primary();
        }

        private Node primary() {
            if (current.kind == TokenKind.NUMBER) {
                BigDecimal value;
                try {
                    value = new BigDecimal(current.text);
                } catch (NumberFormatException failure) {
                    throw error("Invalid number " + current.text);
                }
                advance();
                return new NumberNode(value);
            }
            if (current.kind == TokenKind.VARIABLE) {
                String id = current.text;
                valueReferences.add(id);
                advance();
                return new ValueNode(id);
            }
            if (current.kind == TokenKind.IDENTIFIER) {
                String identifier = current.text;
                advance();
                if (identifier.equalsIgnoreCase("true")) return new NumberNode(BigDecimal.ONE);
                if (identifier.equalsIgnoreCase("false")) return new NumberNode(BigDecimal.ZERO);
                if (current.kind != TokenKind.LEFT) {
                    valueReferences.add(identifier);
                    return new ValueNode(identifier);
                }
                return function(identifier.toLowerCase(Locale.ROOT));
            }
            if (current.kind == TokenKind.LEFT) {
                advance();
                Node nested = expression(0);
                expect(TokenKind.RIGHT, "Expected ')'");
                advance();
                return nested;
            }
            throw error("Expected a value");
        }

        private Node function(String name) {
            expect(TokenKind.LEFT, "Expected '('");
            advance();
            String textArgument = "";
            ArrayList<Node> arguments = new ArrayList<>();
            if (name.equals("lookup")) {
                expect(TokenKind.STRING, "lookup expects a quoted table id");
                textArgument = current.text.trim();
                if (textArgument.isEmpty()) throw error("lookup table id cannot be blank");
                tableReferences.add(textArgument);
                advance();
                expect(TokenKind.COMMA, "lookup expects a key expression");
                advance();
                arguments.add(expression(0));
            } else if (current.kind != TokenKind.RIGHT) {
                do {
                    arguments.add(expression(0));
                    if (current.kind != TokenKind.COMMA) break;
                    advance();
                } while (true);
            }
            expect(TokenKind.RIGHT, "Expected ')' after function arguments");
            advance();
            if (!Set.of("min", "max", "clamp", "abs", "floor", "ceil", "round", "if", "lookup")
                    .contains(name)) {
                throw error("Unknown formula function " + name);
            }
            return new FunctionNode(name, List.copyOf(arguments), textArgument);
        }

        private int precedence(String operator) {
            return switch (operator) {
                case "||" -> 1;
                case "&&" -> 2;
                case "==", "!=" -> 3;
                case "<", "<=", ">", ">=" -> 4;
                case "+", "-" -> 5;
                case "*", "/", "%" -> 6;
                default -> -1;
            };
        }

        private void expect(TokenKind kind, String message) {
            if (current.kind != kind) throw error(message);
        }

        private void advance() {
            current = lexer.next();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + current.offset);
        }
    }

    /** Contesto semplice utile a editor, test e anteprime. */
    public static Context context(Map<String, BigDecimal> values, Map<String, ? extends Map<BigDecimal, BigDecimal>> tables) {
        Map<String, BigDecimal> safeValues = Map.copyOf(values);
        Map<String, ? extends Map<BigDecimal, BigDecimal>> safeTables = Map.copyOf(tables);
        return new Context() {
            @Override public BigDecimal value(String id) {
                return safeValues.get(id);
            }

            @Override public BigDecimal lookup(String tableId, BigDecimal key) {
                Map<BigDecimal, BigDecimal> table = safeTables.get(tableId);
                if (table == null) throw new IllegalArgumentException("Missing formula table " + tableId);
                BigDecimal exact = table.get(key);
                if (exact == null) throw new IllegalArgumentException("Table " + tableId + " has no row for " + key);
                return exact;
            }
        };
    }
}
