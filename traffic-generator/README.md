# ShadowBase Java Traffic Generator

This Java tool generates test INSERT, UPDATE, and DELETE events in the local `shadowbase_prod` PostgreSQL database. Debezium captures these changes and publishes them to the `shadowbase.public.orders` Kafka topic.

The generator creates and deletes its own test orders. It does not change the seeded sample orders.

## Prerequisites

- Docker Desktop
- Java 21 or newer
- Maven
- The ShadowBase Docker Compose services running
- The Debezium PostgreSQL connector in `RUNNING` state

## Start the services

From the ShadowBase project root, run:

```cmd
docker compose up -d
docker compose ps