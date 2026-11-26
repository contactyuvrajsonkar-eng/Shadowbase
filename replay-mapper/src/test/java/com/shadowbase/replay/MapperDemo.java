package com.shadowbase.replay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



public class MapperDemo {

    public static void main(String[] args) {
        DebeziumSqlMapper mapper = new DebeziumSqlMapper();

        Map<String, Object> keyRecord = new LinkedHashMap<>();
        keyRecord.put("schema", Map.of("type", "struct"));
        keyRecord.put("payload", Map.of("id", 11L));

        printPlan("INSERT", mapper.mapDebeziumRecord(
                keyRecord,
                event("c", row("TRAFFIC_NEW", "CDQ="))));

        printPlan("UPDATE", mapper.mapDebeziumRecord(
                keyRecord,
                event("u", row("TRAFFIC_UPDATED", "CJg="))));

        printPlan("DELETE", mapper.mapDebeziumRecord(
                keyRecord,
                event("d", null)));

        System.out.println(
                "TOMBSTONE ignored: "
                        + mapper.mapDebeziumRecord(keyRecord, null).isEmpty());
    }

    private static Map<String, Object> row(String status, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 11L);
        row.put("customer_id", 1L);
        row.put("status", status);
        row.put("total_amount", amount);
        row.put("created_at", "2026-11-25T18:58:42.700908Z");
        return row;
    }

    private static Map<String, Object> event(
            String operation,
            Map<String, Object> after) {

        Map<String, Object> decimalSchema = new LinkedHashMap<>();
        decimalSchema.put("type", "bytes");
        decimalSchema.put("name", "org.apache.kafka.connect.data.Decimal");
        decimalSchema.put("parameters", Map.of("scale", "2"));
        decimalSchema.put("field", "total_amount");

        Map<String, Object> afterSchema = new LinkedHashMap<>();
        afterSchema.put("field", "after");
        afterSchema.put("fields", List.of(decimalSchema));

        Map<String, Object> valueSchema = new LinkedHashMap<>();
        valueSchema.put("fields", List.of(afterSchema));

        Map<String, Object> source = Map.of(
                "db", "shadowbase_prod",
                "schema", "public",
                "table", "orders");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", null);
        payload.put("after", after);
        payload.put("source", source);
        payload.put("op", operation);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schema", valueSchema);
        event.put("payload", payload);
        return event;
    }

    private static void printPlan(
            String label,
            java.util.Optional<DebeziumSqlMapper.SqlPlan> result) {

        if (result.isEmpty()) {
            System.out.println(label + ": ignored");
            return;
        }

        DebeziumSqlMapper.SqlPlan plan = result.get();
        String sql = plan.sql().replaceAll("\\s+", " ").trim();

        System.out.println(label + " SQL: " + sql);
        System.out.println(label + " parameters: " + plan.parameters());
    }
}
