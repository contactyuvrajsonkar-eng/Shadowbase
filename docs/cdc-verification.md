# Debezium CDC Verification

- Connector: `shadowbase-postgres-connector`
- Connector and task status: `RUNNING`
- Kafka topic: `shadowbase.public.orders`
- Verified a test order's INSERT (`op: c`), UPDATE (`op: u`), and DELETE (`op: d`) events with the Kafka console consumer.
- The test order was deleted after verification.