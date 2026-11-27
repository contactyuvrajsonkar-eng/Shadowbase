package com.shadowbase.cdc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns one Kafka message from Debezium into a ChangeEvent.
 * Only the topics, tables and columns listed below are accepted (the allowlist
 * from docs/cdc-replay-design.md).
 */
public class DebeziumParser {

    /** The kinds of column values our two tables use. */
    private enum ColumnType { LONG, TEXT, DECIMAL_SCALE_2, TIMESTAMPTZ }

    private static final String TOPIC_PREFIX = "shadowbase.public.";

    // table -> (column -> type). total_amount is NUMERIC(10,2), so its scale is 2.
    private static final Map<String, Map<String, ColumnType>> ALLOWED = Map.of(
            "customers", columns(
                    "id", ColumnType.LONG,
                    "full_name", ColumnType.TEXT,
                    "email", ColumnType.TEXT,
                    "created_at", ColumnType.TIMESTAMPTZ),
            "orders", columns(
                    "id", ColumnType.LONG,
                    "customer_id", ColumnType.LONG,
                    "status", ColumnType.TEXT,
                    "total_amount", ColumnType.DECIMAL_SCALE_2,
                    "created_at", ColumnType.TIMESTAMPTZ));

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param topic     the Kafka topic the message came from
     * @param keyJson   the message key as JSON text, for example {"id":4}
     * @param valueJson the message value as JSON text, or null for a tombstone
     * @throws IllegalArgumentException if the message is malformed
     */
    public ChangeEvent parse(String topic, String keyJson, String valueJson) {
        String table = tableOf(topic);
        if (table == null) {
            return new ChangeEvent(topic, Operation.UNSUPPORTED, null, Map.of(), 0);
        }

        Long id = readKeyId(keyJson);

        // A Kafka message with a null value is the "tombstone" Debezium sends after a delete
        if (valueJson == null) {
            return new ChangeEvent(table, Operation.TOMBSTONE, id, Map.of(), 0);
        }

        JsonNode event = unwrap(readTree(valueJson));
        Operation op = Operation.fromCode(event.path("op").asText(null));
        long tsMs = event.path("ts_ms").asLong(0);

        if (op == Operation.UNSUPPORTED) {
            return new ChangeEvent(table, op, id, Map.of(), tsMs);
        }

        if (op == Operation.DELETE) {
            // PostgreSQL may put only the primary key in "before", so the id comes from the key
            requireId(id);
            return new ChangeEvent(table, op, id, Map.of(), tsMs);
        }

        // r, c and u: the new row is in "after"
        Map<String, Object> after = convertRow(table, event.path("after"));
        if (after.isEmpty()) {
            throw new IllegalArgumentException("Event " + op + " has no 'after' row");
        }
        if (id == null) {
            id = (Long) after.get("id");
        }
        requireId(id);
        return new ChangeEvent(table, op, id, after, tsMs);
    }

    private static void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Event has no row id in the Kafka key");
        }
    }

    private String tableOf(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String table = topic.substring(TOPIC_PREFIX.length());
        return ALLOWED.containsKey(table) ? table : null;
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    // If the connector ever sends schemas, the real data sits under "payload"; otherwise it is flat
    private JsonNode unwrap(JsonNode root) {
        JsonNode payload = root.get("payload");
        return (payload != null && payload.isObject()) ? payload : root;
    }

    private Long readKeyId(String keyJson) {
        if (keyJson == null) {
            return null;
        }
        JsonNode id = unwrap(readTree(keyJson)).get("id");
        return (id != null && id.canConvertToLong()) ? id.asLong() : null;
    }

    private Map<String, Object> convertRow(String table, JsonNode after) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (after == null || !after.isObject()) {
            return row;
        }
        for (Map.Entry<String, ColumnType> column : ALLOWED.get(table).entrySet()) {
            JsonNode value = after.get(column.getKey());
            if (value == null) {
                continue; // this column is not in the event
            }
            row.put(column.getKey(), value.isNull() ? null : convert(column.getValue(), value));
        }
        return row;
    }

    private Object convert(ColumnType type, JsonNode value) {
        return switch (type) {
            case LONG -> Long.valueOf(value.asLong());
            case TEXT -> value.asText();
            case DECIMAL_SCALE_2 -> toDecimal(value);
            case TIMESTAMPTZ -> OffsetDateTime.parse(value.asText());
        };
    }

    // Debezium's default "precise" mode sends NUMERIC as base64 text of the unscaled number.
    // Example: "BNI=" -> bytes 04 D2 -> 1234 -> with scale 2 -> 12.34
    // Assumption: the connector keeps the default decimal.handling.mode.
    private BigDecimal toDecimal(JsonNode value) {
        if (value.isNumber()) {
            return value.decimalValue();
        }
        byte[] unscaled = Base64.getDecoder().decode(value.asText());
        return new BigDecimal(new BigInteger(unscaled), 2);
    }

    private static Map<String, ColumnType> columns(Object... nameAndType) {
        Map<String, ColumnType> map = new LinkedHashMap<>();
        for (int i = 0; i < nameAndType.length; i += 2) {
            map.put((String) nameAndType[i], (ColumnType) nameAndType[i + 1]);
        }
        return map;
    }
}