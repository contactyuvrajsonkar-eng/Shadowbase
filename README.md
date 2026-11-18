# ShadowBase

ShadowBase is a zero-downtime schema migration sandbox. It captures changes from a mock PostgreSQL production database using Debezium and streams them to Kafka.

## Local Setup and CDC Verification

### Prerequisites

- Docker Desktop
- Postman

### Start the services

Run these commands from the project root:

```cmd
docker compose up -d
docker compose ps
```

PostgreSQL should show as healthy, and Kafka and Debezium Connect should be running.

### Register the Debezium connector

In Postman, create a `POST` request:

- URL: `http://localhost:8083/connectors`
- Body: Select **raw JSON** and paste the contents of `infra/kafka/register-postgres.json`.

Register the connector once. If it is already registered, skip this step and check its status.

### Check connector status

Run this command in CMD:

```cmd
curl.exe -s http://localhost:8083/connectors/shadowbase-postgres-connector/status
```

The connector and its task should show `RUNNING`.

### Read CDC events from Kafka

Run this command from the project root:

```cmd
docker compose exec kafka /kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic shadowbase.public.orders --from-beginning
```

The topic contains order change events. Check the event's `payload.op` field:

- `r`: snapshot event
- `c`: insert event
- `u`: update event
- `d`: delete event

A `null` record after a delete may appear as a Kafka tombstone.

### Stop the services

```cmd
docker compose stop
```

This stops the services without removing the database volume.