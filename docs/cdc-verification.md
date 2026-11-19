# Debezium CDC Verification

- Connector: `shadowbase-postgres-connector`
- Connector and task status: `RUNNING`
- Kafka topic: `shadowbase.public.orders`
- Verified a test order's INSERT (`op: c`), UPDATE (`op: u`), and DELETE (`op: d`) events with the Kafka console consumer.
- The test order was deleted after verification.-

## Kafka Event Structure

The Kafka consumer output is a JSON record with a `schema` and a `payload`. The row-change data is inside `payload`.

- `payload.op`: operation code
- `payload.before`: row before the change
- `payload.after`: row after the change

Operation codes observed:

- `c`: insert (`before` is null; `after` contains the inserted row)
- `u`: update (`before` and `after` contain the row values)
- `d`: delete (`after` is null; `before` contains the deleted row)