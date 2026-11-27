package com.shadowbase.cdc;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Simple test consumer: reads the Debezium row-change events of the ShadowBase tables
 * and prints each one, both raw and translated into a ChangeEvent.
 *
 * Run:  mvnw compile exec:java                         (reads everything, stops after 20 s of silence)
 *       mvnw compile exec:java -Dexec.args="--follow"  (keeps waiting for new events)
 */
public class TestConsumer {

    private static final List<String> TOPICS =
            List.of("shadowbase.public.customers", "shadowbase.public.orders");

    // Stop by itself when nothing new arrives for this long (unless --follow)
    private static final long IDLE_EXIT_MS = 20_000;

    // If the broker has not answered after this long, print a hint
    private static final long HINT_AFTER_MS = 15_000;

    public static void main(String[] args) {
        // Keep the console readable: show only warnings from Kafka's own logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

        boolean follow = List.of(args).contains("--follow");
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        // Kafka remembers a "bookmark" (offset) for each group id
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "shadowbase-test-consumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // A group with no bookmark starts at the oldest message...
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // ...and because we never save a bookmark, every run reads everything again
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        DebeziumParser parser = new DebeziumParser();
        Thread mainThread = Thread.currentThread();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

            // Ctrl+C: wakeup() makes the waiting poll() stop, so we can close cleanly
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                consumer.wakeup();
                try {
                    mainThread.join(2000);
                } catch (InterruptedException ignored) {
                    // shutting down anyway
                }
            }));

            consumer.subscribe(TOPICS);
            System.out.println("Reading " + TOPICS + " from " + bootstrap + " ...");

            long started = System.currentTimeMillis();
            long lastActivity = started;
            boolean hintShown = false;
            int total = 0;

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                long now = System.currentTimeMillis();

                for (ConsumerRecord<String, String> record : records) {
                    total++;
                    print(parser, record);
                }

                if (!records.isEmpty() || consumer.assignment().isEmpty()) {
                    // Not connected yet (or just got data): the silence timer does not run
                    lastActivity = now;
                } else if (!follow && now - lastActivity > IDLE_EXIT_MS) {
                    break;
                }

                if (!hintShown && consumer.assignment().isEmpty() && now - started > HINT_AFTER_MS) {
                    System.out.println("Not connected yet. Is the stack running ('docker compose ps'), "
                            + "and does your hosts file contain '127.0.0.1 kafka'?");
                    hintShown = true;
                }
            }

            System.out.println();
            System.out.println("Done. Read " + total + " messages. Use --follow to keep waiting for new ones.");

        } catch (WakeupException e) {
            System.out.println("Stopped.");
        }
    }

    private static void print(DebeziumParser parser, ConsumerRecord<String, String> record) {
        System.out.println();
        System.out.println("--- " + record.topic() + " [partition " + record.partition()
                + ", offset " + record.offset() + "]");
        System.out.println("key:   " + record.key());
        System.out.println("value: " + (record.value() == null ? "null (tombstone)" : record.value()));
        try {
            ChangeEvent event = parser.parse(record.topic(), record.key(), record.value());
            System.out.println("=>     " + describe(event));
        } catch (RuntimeException e) {
            System.out.println("=>     could not parse: " + e.getMessage());
        }
    }

    private static String describe(ChangeEvent e) {
        return switch (e.op()) {
            case TOMBSTONE -> "TOMBSTONE for " + e.table() + " id=" + e.id() + " (ignored)";
            case UNSUPPORTED -> "UNSUPPORTED event on " + e.table() + " (ignored)";
            case DELETE -> "DELETE " + e.table() + " id=" + e.id();
            default -> e.op() + " " + e.table() + " id=" + e.id() + " " + e.after();
        };
    }
}
