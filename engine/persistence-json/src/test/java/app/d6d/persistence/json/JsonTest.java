package app.d6d.persistence.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void encodesScalarsUsingJsonSyntax() {
        assertEquals("null", Json.encode(null));
        assertEquals("true", Json.encode(true));
        assertEquals("false", Json.encode(false));
        assertEquals("42", Json.encode(42));
        assertEquals("-9223372036854775808", Json.encode(Long.MIN_VALUE));
        assertEquals("123456789012345678901234567890",
                Json.encode(new BigInteger("123456789012345678901234567890")));
        assertEquals("-12.3400", Json.encode(new BigDecimal("-12.3400")));
        assertEquals("\"x\"", Json.encode('x'));
    }

    @Test
    void encodesMapsListsAndEveryKindOfArray() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "goblin");
        value.put("hp", 7);
        value.put("position", new int[] {2, 3});
        value.put("flags", List.of(true, false));
        value.put("nullable", new Object[] {"present", null});

        assertEquals(
                "{\"name\":\"goblin\",\"hp\":7,\"position\":[2,3],"
                        + "\"flags\":[true,false],\"nullable\":[\"present\",null]}",
                Json.stringify(value));
    }

    @Test
    void escapesQuotesBackslashesControlsAndUnicodeAsAsciiSafeJson() {
        String value = "\"\\\b\f\n\r\t\u0000\u001fé😀";

        String encoded = Json.encode(value);

        assertEquals(
                "\"\\\"\\\\\\b\\f\\n\\r\\t\\u0000\\u001F\\u00E9\\uD83D\\uDE00\"",
                encoded);
        assertEquals(value, Json.parse(encoded));
    }

    @Test
    void parsesObjectsArraysWhitespaceAndAllScalarKinds() {
        Object decoded = Json.parse("""
                {
                  "nothing": null,
                  "enabled": true,
                  "disabled": false,
                  "small": 2147483647,
                  "large": 2147483648,
                  "huge": 9223372036854775808,
                  "decimal": -6.022e23,
                  "items": ["sword", 2]
                }
                """);

        Map<?, ?> object = assertInstanceOf(Map.class, decoded);
        assertEquals(null, object.get("nothing"));
        assertEquals(Boolean.TRUE, object.get("enabled"));
        assertEquals(Boolean.FALSE, object.get("disabled"));
        assertEquals(2147483647, object.get("small"));
        assertEquals(2147483648L, object.get("large"));
        assertEquals(new BigInteger("9223372036854775808"), object.get("huge"));
        assertEquals(new BigDecimal("-6.022e23"), object.get("decimal"));
        assertEquals(List.of("sword", 2), object.get("items"));
    }

    @Test
    void parsesEveryEscapeIncludingSurrogatePairs() {
        assertEquals("\"\\/\b\f\n\r\tAé😀",
                Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\u00e9\\uD83D\\uDE00\""));
        assertEquals("unescaped café 😀", Json.parse("\"unescaped café 😀\""));
    }

    @Test
    void supportsLosslessCanonicalRoundTrips() {
        String source = """
                {
                  "campaign": "Le miniere",
                  "combatants": [
                    {"name":"Aria","hp":19,"conditions":[]},
                    {"name":"Drago 🐉","hp":123456789012345678901,"conditions":["prone"]}
                  ],
                  "ratio": 0.1000000000000000000000000001,
                  "active": true
                }
                """;

        Object first = Json.parse(source);
        String canonical = Json.toJson(first);
        Object second = Json.fromJson(canonical);

        assertEquals(first, second);
        assertEquals(canonical, Json.encode(second));
        assertTrue(canonical.contains("Drago \\uD83D\\uDC09"));
    }

    @Test
    void parseObjectReturnsStringKeyedInsertionOrderedMap() {
        Map<String, Object> object = Json.parseObject("{\"b\":2,\"a\":1}");

        assertEquals(List.of("b", "a"), new ArrayList<>(object.keySet()));
        assertEquals(2, object.get("b"));

        Json.JsonParseException error = assertThrows(
                Json.JsonParseException.class,
                () -> Json.parseObject("[1,2]"));
        assertTrue(error.getMessage().contains("document root"));
    }

    @Test
    void reportsSyntaxErrorsWithLineColumnAndOffset() {
        Json.JsonParseException error = assertThrows(
                Json.JsonParseException.class,
                () -> Json.parse("{\n  \"hp\": 7,\n  broken\n}"));

        assertEquals(3, error.line());
        assertEquals(3, error.column());
        assertEquals(15, error.offset());
        assertTrue(error.getMessage().contains("quoted object key"));
        assertTrue(error.getMessage().contains("line 3, column 3"));
    }

    @Test
    void rejectsMalformedDocumentsInsteadOfPartiallyParsingThem() {
        List<String> malformed = List.of(
                "",
                "true false",
                "nul",
                "[1,]",
                "{\"x\":1,}",
                "{\"x\" 1}",
                "01",
                "-",
                "1.",
                "1e+",
                "\"line\nfeed\"",
                "\"\\x\"",
                "\"\\u12xz\"",
                "{\"same\":1,\"same\":2}");

        for (String json : malformed) {
            assertThrows(Json.JsonParseException.class, () -> Json.parse(json), json);
        }
    }

    @Test
    void rejectsMalformedUnicodeRatherThanCreatingInvalidStrings() {
        assertThrows(Json.JsonParseException.class, () -> Json.parse("\"\\uD83D\""));
        assertThrows(Json.JsonParseException.class, () -> Json.parse("\"\\uDE00\""));
        assertThrows(Json.JsonParseException.class, () -> Json.parse("\"\\uD83D\\u0041\""));
        assertThrows(IllegalArgumentException.class, () -> Json.encode("\uD83D"));
        assertThrows(IllegalArgumentException.class, () -> Json.encode("\uDE00"));
    }

    @Test
    void rejectsUnsupportedValuesInvalidKeysAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.encode(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(Map.of(1, "not a JSON key")));
    }

    @Test
    void detectsCyclesButAllowsRepeatedNonCyclicValues() {
        List<Object> cycle = new ArrayList<>();
        cycle.add("start");
        cycle.add(cycle);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Json.encode(cycle));
        assertTrue(error.getMessage().contains("cyclic reference"));
        assertTrue(error.getMessage().contains("$[1]"));

        List<Object> shared = new ArrayList<>();
        shared.add(1);
        assertEquals("[[1],[1]]", Json.encode(List.of(shared, shared)));
    }
}
