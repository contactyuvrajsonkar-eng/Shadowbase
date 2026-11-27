package com.shadowbase.cdc;

import java.util.Map;

/**
 * One row change, already translated into plain Java values.
 *
 * @param table the table name: "customers" or "orders"
 * @param op    what happened to the row
 * @param id    the row's primary key (taken from the Kafka key)
 * @param after the new column values (only for SNAPSHOT_READ, CREATE and UPDATE), else empty
 * @param tsMs  when Debezium saw the change, in milliseconds since 1970 (0 if unknown)
 */
public record ChangeEvent(String table, Operation op, Long id, Map<String, Object> after, long tsMs) {
}