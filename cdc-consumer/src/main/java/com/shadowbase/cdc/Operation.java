package com.shadowbase.cdc;

/** What happened to a row, translated from Debezium's one-letter "op" code. */
public enum Operation {
    SNAPSHOT_READ,  // "r": a row copied during Debezium's first full read of the table
    CREATE,         // "c": INSERT
    UPDATE,         // "u": UPDATE
    DELETE,         // "d": DELETE
    TOMBSTONE,      // a Kafka message with a key but a null value, sent after a delete
    UNSUPPORTED;    // anything else (truncate, unknown topic, ...)

    static Operation fromCode(String code) {
        if (code == null) {
            return UNSUPPORTED;
        }
        return switch (code) {
            case "r" -> SNAPSHOT_READ;
            case "c" -> CREATE;
            case "u" -> UPDATE;
            case "d" -> DELETE;
            default -> UNSUPPORTED;
        };
    }
}
