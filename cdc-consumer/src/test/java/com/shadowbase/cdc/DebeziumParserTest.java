package com.shadowbase.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/**
 * Unit tests with sample events shaped like Debezium's output
 * (schemas disabled, as in docker-compose.yml). No Kafka or Docker needed.
 */
class DebeziumParserTest {

    private static final String ORDERS = "shadowbase.public.orders";
    private static final String KEY_4 = "{\"id\":4}";

    private final DebeziumParser parser = new DebeziumParser();

    private static String order(String status, String totalBase64) {
        return "{\"id\":4,\"customer_id\":3,\"status\":\"" + status
                + "\",\"total_amount\":\"" + totalBase64
                + "\",\"created_at\":\"2026-11-26T10:00:00.123456Z\"}";
    }

    private static String event(String op, String before, String after) {
        return "{\"before\":" + before + ",\"after\":" + after
                + ",\"source\":{\"connector\":\"postgresql\",\"name\":\"shadowbase\","
                + "\"db\":\"shadowbase_prod\",\"schema\":\"public\",\"table\":\"orders\"}"
                + ",\"op\":\"" + op + "\",\"ts_ms\":1764151200000}";
    }

    @Test
    void insert_isParsedWithTypedValues() {
        ChangeEvent e = parser.parse(ORDERS, KEY_4, event("c", "null", order("CDC_TEST", "BNI=")));

        assertEquals(Operation.CREATE, e.op());
        assertEquals("orders", e.table());
        assertEquals(Long.valueOf(4), e.id());
        assertEquals("CDC_TEST", e.after().get("status"));
        assertEquals(Long.valueOf(3), e.after().get("customer_id"));
        assertEquals(new BigDecimal("12.34"), e.after().get("total_amount"));
        assertEquals(OffsetDateTime.parse("2026-11-26T10:00:00.123456Z"), e.after().get("created_at"));
        assertEquals(1764151200000L, e.tsMs());
    }

    @Test
    void snapshotRead_ofSeededOrder_keepsTheExactAmount() {
        // "AkmM" is 1499.00 encoded the way Debezium sends NUMERIC(10,2)
        String after = "{\"id\":1,\"customer_id\":1,\"status\":\"PROCESSING\",\"total_amount\":\"AkmM\","
                + "\"created_at\":\"2026-11-20T08:26:19.225529Z\"}";

        ChangeEvent e = parser.parse(ORDERS, "{\"id\":1}", event("r", "null", after));

        assertEquals(Operation.SNAPSHOT_READ, e.op());
        assertEquals(new BigDecimal("1499.00"), e.after().get("total_amount"));
    }

    @Test
    void update_usesTheAfterRow() {
        ChangeEvent e = parser.parse(ORDERS, KEY_4,
                event("u", order("CDC_TEST", "BNI="), order("CDC_UPDATED", "BNI=")));

        assertEquals(Operation.UPDATE, e.op());
        assertEquals("CDC_UPDATED", e.after().get("status"));
    }

    @Test
    void delete_usesTheKeyAndNeedsNoRowData() {
        // PostgreSQL may send only the primary key in "before"
        ChangeEvent e = parser.parse(ORDERS, KEY_4, event("d", "{\"id\":4}", "null"));

        assertEquals(Operation.DELETE, e.op());
        assertEquals(Long.valueOf(4), e.id());
        assertTrue(e.after().isEmpty());
    }

    @Test
    void nullValue_isATombstone() {
        ChangeEvent e = parser.parse(ORDERS, KEY_4, null);

        assertEquals(Operation.TOMBSTONE, e.op());
        assertEquals(Long.valueOf(4), e.id());
    }

    @Test
    void eventWithSchemaAndPayload_isParsedToo() {
        String wrappedKey = "{\"schema\":{\"type\":\"struct\"},\"payload\":{\"id\":4}}";
        String wrappedValue = "{\"schema\":{\"type\":\"struct\"},\"payload\":"
                + event("c", "null", order("CDC_TEST", "BNI=")) + "}";

        ChangeEvent e = parser.parse(ORDERS, wrappedKey, wrappedValue);

        assertEquals(Operation.CREATE, e.op());
        assertEquals(Long.valueOf(4), e.id());
        assertEquals(new BigDecimal("12.34"), e.after().get("total_amount"));
    }

    @Test
    void unknownTopicOrOperation_isUnsupported() {
        assertEquals(Operation.UNSUPPORTED,
                parser.parse("shadowbase.public.secrets", KEY_4, "{}").op());
        assertEquals(Operation.UNSUPPORTED,
                parser.parse(ORDERS, KEY_4, event("t", "null", "null")).op());
    }

    @Test
    void createWithoutAfterRow_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(ORDERS, KEY_4, event("c", "null", "null")));
    }
}