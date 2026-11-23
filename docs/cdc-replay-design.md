# CDC Row Event to SQL Replay Design

## Scope

Consume Debezium events from `shadowbase.public.customers` and
`shadowbase.public.orders`, then apply the corresponding row changes to the
sandbox PostgreSQL database.

The source database is read-only for the replay service. All generated SQL
must run against the sandbox database.

## Event Handling

Before processing an event, allow only the expected topics and the
`public.customers` and `public.orders` tables.

| Input | Replay action |
|---|---|
| Null Kafka value | Ignore. This is a Debezium delete tombstone. |
| `op = "r"` | Upsert the snapshot row from `payload.after`. |
| `op = "c"` | Upsert the inserted row from `payload.after`. |
| `op = "u"` | Update the sandbox row using `payload.after`; use the Kafka key for the row ID. |
| `op = "d"` | Delete by the row ID in the Kafka key. |
| Other operation | Record as unsupported; do not generate SQL. |

With PostgreSQL's default `REPLICA IDENTITY`, a delete event's `before` image
may contain only the primary key. Use the Kafka record key for deletes. A
primary-key change is represented by Debezium as a delete and a create event.

## SQL Rules

Use fixed, allowlisted table and column names. Bind all row values with JDBC
`PreparedStatement` parameters; never concatenate event values into SQL.

For `customers`, the allowed columns are:
`id`, `full_name`, `email`, `created_at`.

For `orders`, the allowed columns are:
`id`, `customer_id`, `status`, `total_amount`, `created_at`.

Snapshot and create events use `INSERT ... ON CONFLICT (id) DO UPDATE` so
reprocessing the same event is safe.

Update events use `UPDATE ... SET ... WHERE id = ?`.
If the update matches no row, report a replay error instead of silently
skipping it.

Delete events use `DELETE ... WHERE id = ?`. Repeating the delete is safe.

## Data Types

Convert values to their PostgreSQL types before binding them. Preserve
`total_amount` as `BigDecimal`; do not convert it through `double`.
Convert `created_at` to a timezone-aware Java time value before binding.

## Failure Handling

Apply each event in a database transaction. Commit the transaction first, then
commit the Kafka offset. On SQL failure, roll back and record the event and
error so it can be investigated or retried.