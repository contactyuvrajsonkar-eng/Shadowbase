\# ShadowBase CDC test consumer



A small Java program that reads the Debezium row-change events of the ShadowBase tables

from Kafka and prints them, both raw and translated into plain Java values.

It is the first step towards the CDC-to-sandbox replay described in `docs/cdc-replay-design.md`.



\## What it does



\- Subscribes to `shadowbase.public.customers` and `shadowbase.public.orders`.

\- Reads from the beginning of each topic and never saves its position, so every run reads everything again.

\- Parses each message with `DebeziumParser`: insert/snapshot (`r`, `c`), update (`u`), delete (`d`), and ignores tombstones.

\- Converts values to Java types: ids to `Long`, `created\_at` to `OffsetDateTime`, and `total\_amount` to an exact `BigDecimal`.



\## Prerequisites



\- Java 21 and Docker Desktop

\- The ShadowBase stack running (`docker compose up -d`) and the connector in `RUNNING` state

\- The line `127.0.0.1 kafka` in `C:\\Windows\\System32\\drivers\\etc\\hosts`

&#x20; (Kafka announces itself as `kafka:9092`, which only Docker knows by default)



\## Run



```cmd

mvnw compile exec:java

mvnw compile exec:java -Dexec.args="--follow"

```



The first command stops after 20 seconds without new messages. `--follow` keeps waiting for new events (stop with Ctrl+C).

Set `KAFKA\_BOOTSTRAP\_SERVERS` to use another address (default `localhost:9092`).



\## Tests



```cmd

mvnw test

```



The parser tests use sample events shaped like Debezium's output. No Kafka or Docker is needed.



\## Notes and assumptions



\- The connector sends `NUMERIC` as base64 text (Debezium's default `precise` mode), for example `"BNI="` is `12.34`.

&#x20; The parser decodes it with scale 2, which matches `NUMERIC(10,2)`. If the connector's decimal mode changes, this must change too.

\- docker-compose.yml turns off schemas, so events have no `payload` wrapper. The parser accepts both shapes.

\- This consumer only prints. Applying events to a sandbox database is the next step.

