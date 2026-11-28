package com.shadowbase.replay;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigInteger;
import java.util.Base64;
import java.util.LinkedHashMap;
public final class DebeziumSqlMapper {

    private static final String SOURCE_DATABASE = "shadowbase_prod";
    private static final String SOURCE_SCHEMA = "public";
    public Optional<SqlPlan> mapDebeziumRecord(
        Map<String, Object> keyRecord,
        Map<String, Object> valueRecord) {

    // A Kafka tombstone has a null value.
    if (valueRecord == null) {
        return Optional.empty();
    }

    Map<String, Object> envelope = unwrapPayload(valueRecord, "value");
    Map<String, Object> source = requiredMap(envelope, "source");

    Map<String, Object> key = keyRecord == null
            ? null
            : unwrapPayload(keyRecord, "key");

    Map<String, Object> after = optionalMap(envelope.get("after"), "after");
    Map<String, Object> valueSchema =
            optionalMap(valueRecord.get("schema"), "schema");

    after = decodeDebeziumDecimal(valueSchema, after);

    return map(
            requiredText(source, "db"),
            requiredText(source, "schema"),
            requiredText(source, "table"),
            requiredText(envelope, "op"),
            key,
            after);
}

private static Map<String, Object> unwrapPayload(
        Map<String, Object> record,
        String label) {

    Object payload = record.get("payload");

    if (payload instanceof Map<?, ?> payloadMap) {
        return stringKeyMap(payloadMap, label + ".payload");
    }

    if (record.containsKey("payload") || record.containsKey("schema")) {
        throw new IllegalArgumentException(
                "Missing or invalid Debezium payload in " + label);
    }

    // Also accept an already-unwrapped record.
    return record;
}

private static Map<String, Object> requiredMap(
        Map<String, Object> record,
        String field) {

    Object value = record.get(field);
    if (!(value instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException(
                "Missing or invalid map field: " + field);
    }

    return stringKeyMap(map, field);
}

private static Map<String, Object> optionalMap(
        Object value,
        String field) {

    if (value == null) {
        return null;
    }

    if (!(value instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException(
                "Invalid map field: " + field);
    }

    return stringKeyMap(map, field);
}

private static Map<String, Object> stringKeyMap(
        Map<?, ?> input,
        String label) {

    Map<String, Object> result = new LinkedHashMap<>();

    for (Map.Entry<?, ?> entry : input.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
            throw new IllegalArgumentException(
                    "Non-string key in " + label);
        }
        result.put(key, entry.getValue());
    }

    return result;
}

private static Map<String, Object> findFieldSchema(
        Map<String, Object> schema,
        String fieldName) {

    if (schema == null) {
        return null;
    }

    Object fieldsValue = schema.get("fields");
    if (!(fieldsValue instanceof List<?> fields)) {
        return null;
    }

    for (Object fieldValue : fields) {
        if (fieldValue instanceof Map<?, ?> fieldMap) {
            Map<String, Object> field =
                    stringKeyMap(fieldMap, "schema field");

            if (fieldName.equals(field.get("field"))) {
                return field;
            }
        }
    }

    return null;
}

private static Map<String, Object> decodeDebeziumDecimal(
        Map<String, Object> valueSchema,
        Map<String, Object> after) {

    if (valueSchema == null || after == null) {
        return after;
    }

    Map<String, Object> afterSchema =
            findFieldSchema(valueSchema, "after");
    Map<String, Object> amountSchema =
            findFieldSchema(afterSchema, "total_amount");

    if (amountSchema == null
            || !"bytes".equals(amountSchema.get("type"))
            || !"org.apache.kafka.connect.data.Decimal"
                    .equals(amountSchema.get("name"))) {
        return after;
    }

    Object amountValue = after.get("total_amount");
    if (!(amountValue instanceof String encodedAmount)) {
        return after;
    }

    Map<String, Object> parameters =
            optionalMap(amountSchema.get("parameters"), "parameters");

    Object scaleValue = parameters == null
            ? null
            : parameters.get("scale");

    if (scaleValue == null) {
        throw new IllegalArgumentException(
                "Debezium Decimal schema is missing its scale");
    }

    try {
        int scale = Integer.parseInt(scaleValue.toString());
        byte[] unscaledBytes =
                Base64.getDecoder().decode(encodedAmount);

        if (unscaledBytes.length == 0) {
            throw new IllegalArgumentException(
                    "Debezium Decimal value is empty");
        }

        BigDecimal decodedAmount =
                new BigDecimal(new BigInteger(unscaledBytes), scale);

        Map<String, Object> normalizedAfter =
                new LinkedHashMap<>(after);
        normalizedAfter.put("total_amount", decodedAmount);
        return normalizedAfter;

    } catch (IllegalArgumentException e)  {
        throw new IllegalArgumentException(
                "Could not decode Debezium total_amount Decimal", e);
    }
}
    public Optional<SqlPlan> map(
            String sourceDatabase,
            String schema,
            String table,
            String operation,
            Map<String, Object> key,
            Map<String, Object> after) {

        // A null operation means this Kafka record is a delete tombstone.
        if (operation == null) {
            return Optional.empty();
        }

        if (!SOURCE_DATABASE.equals(sourceDatabase)) {
            throw new IllegalArgumentException("Unexpected source database");
        }

        if (!SOURCE_SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported schema: " + schema);
        }

        if (!"customers".equals(table) && !"orders".equals(table)) {
            throw new IllegalArgumentException("Unsupported table: " + table);
        }

        return switch (operation) {
            case "r", "c" -> Optional.of(upsert(table, key, after));
            case "u" -> Optional.of(update(table, key, after));
            case "d" -> Optional.of(delete(table, key));
            default -> throw new IllegalArgumentException(
                    "Unsupported Debezium operation: " + operation);
        };
    }

    private SqlPlan upsert(String table, Map<String, Object> key,
                           Map<String, Object> after) {
        long id = matchingId(key, after);

        if ("customers".equals(table)) {
            String sql = """
                    INSERT INTO public.customers (id, full_name, email, created_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        full_name = EXCLUDED.full_name,
                        email = EXCLUDED.email,
                        created_at = EXCLUDED.created_at
                    """;

            return new SqlPlan(sql, List.of(
                    id,
                    requiredText(after, "full_name"),
                    requiredText(after, "email"),
                    requiredTimestamp(after, "created_at")));
        }

        String sql = """
                INSERT INTO public.orders
                    (id, customer_id, status, total_amount, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id,
                    status = EXCLUDED.status,
                    total_amount = EXCLUDED.total_amount,
                    created_at = EXCLUDED.created_at
                """;

        return new SqlPlan(sql, List.of(
                id,
                requiredLong(after, "customer_id"),
                requiredText(after, "status"),
                requiredDecimal(after, "total_amount"),
                requiredTimestamp(after, "created_at")));
    }

    private SqlPlan update(String table, Map<String, Object> key,
                           Map<String, Object> after) {
        long id = matchingId(key, after);

        if ("customers".equals(table)) {
            String sql = """
                    UPDATE public.customers
                    SET full_name = ?, email = ?, created_at = ?
                    WHERE id = ?
                    """;

            return new SqlPlan(sql, List.of(
                    requiredText(after, "full_name"),
                    requiredText(after, "email"),
                    requiredTimestamp(after, "created_at"),
                    id));
        }

        String sql = """
                UPDATE public.orders
                SET customer_id = ?, status = ?, total_amount = ?, created_at = ?
                WHERE id = ?
                """;

        return new SqlPlan(sql, List.of(
                requiredLong(after, "customer_id"),
                requiredText(after, "status"),
                requiredDecimal(after, "total_amount"),
                requiredTimestamp(after, "created_at"),
                id));
    }

    private SqlPlan delete(String table, Map<String, Object> key) {
        long id = requiredLong(key, "id");
        String sql = "DELETE FROM public." + table + " WHERE id = ?";
        return new SqlPlan(sql, List.of(id));
    }

    private long matchingId(Map<String, Object> key, Map<String, Object> after) {
        long keyId = requiredLong(key, "id");
        long rowId = requiredLong(after, "id");

        if (keyId != rowId) {
            throw new IllegalArgumentException(
                    "Kafka key id does not match payload row id");
        }

        return keyId;
    }

    private static Object required(Map<String, Object> row, String field) {
        if (row == null || !row.containsKey(field) || row.get(field) == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return row.get(field);
    }

    private static String requiredText(Map<String, Object> row, String field) {
        Object value = required(row, field);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(field + " must be text");
    }

    private static long requiredLong(Map<String, Object> row, String field) {
        Object value = required(row, field);
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException(field + " must be a whole number", e);
            }
        }
        throw new IllegalArgumentException(field + " must be numeric");
    }

    private static BigDecimal requiredDecimal(Map<String, Object> row, String field) {
        Object value = required(row, field);

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        field + " must be decoded to a decimal value first", e);
            }
        }

        throw new IllegalArgumentException(
                field + " must be BigDecimal or an exact decimal string");
    }

    private static OffsetDateTime requiredTimestamp(
            Map<String, Object> row, String field) {
        Object value = required(row, field);

        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }

        if (value instanceof String text) {
            return OffsetDateTime.parse(text);
        }

        throw new IllegalArgumentException(
                field + " must be an ISO-8601 timestamp or OffsetDateTime");
    }

    public record SqlPlan(String sql, List<Object> parameters) {
        public SqlPlan {
            parameters = List.copyOf(parameters);
        }
    }
}