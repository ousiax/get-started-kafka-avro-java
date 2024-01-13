package com.example;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.example.avro.User;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

public class Main {
    public static void main(String[] args) throws IOException {
        User user1 = new User();
        user1.setName("Alyssa");
        user1.setFavoriteNumber(256);
        // Leave favorite color null

        // Alternate constructor
        User user2 = new User("Ben", 7, "red");

        // Construct via builder
        User user3 = User.newBuilder()
                .setName("Charlie")
                .setFavoriteColor("blue")
                .setFavoriteNumber(null)
                .build();

        final List<User> users = List.of(user1, user2, user3);
        final String topic = "customerContacts";
        final String bootstrapServers = "node-1:9092,node-2:9092";
        final String schemaUrl = "http://node-2:8081";

        ExecutorService executor = Executors.newCachedThreadPool();

        executor.submit(() -> {
            Properties props = new Properties();
            // props.put("bootstrap.servers", bootstrapServers);
            // props.put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
            // props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
            // props.put("schema.registry.url", schemaUrl);
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    io.confluent.kafka.serializers.KafkaAvroSerializer.class);
            props.put("schema.registry.url", schemaUrl);

            try (Producer<String, User> producer = new KafkaProducer<String, User>(props);) {
                // We keep producing new events until someone ctrl-c
                while (true) {
                    try {
                        Thread.sleep(Duration.ofSeconds(3));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (Thread.interrupted()) {
                        break;
                    }

                    int index = new Random().nextInt(3);
                    User customer = users.get(index);
                    System.out.println("Generated customer " + customer.toString());
                    ProducerRecord<String, User> record = new ProducerRecord<>(
                            topic,
                            customer.getName().toString(),
                            customer);
                    producer.send(record);
                }
            } catch (Exception e) {
                System.err.println("Producer: " + e.getMessage());
                e.printStackTrace();
            }
        });

        executor.submit(() -> {
            Properties props = new Properties();
            // props.put("bootstrap.servers", bootstrapServers);
            // props.put("group.id", "CountryCounter");
            // props.put("key.serializer", "org.apache.kafka.common.serialization.StringDeserializer");
            // props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroDeserializer");
            // props.put("schema.registry.url", schemaUrl);
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "executor");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
            props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
            props.put("schema.registry.url", schemaUrl);

            try (Consumer<String, User> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(topic));
                System.out.println("Reading topic:" + topic);
                while (true) {
                    try {
                        Thread.sleep(Duration.ofSeconds(1));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (Thread.interrupted()) {
                        break;
                    }

                    ConsumerRecords<String, User> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, User> record : records) {
                        System.out.println("Current customer name is: " + record.value().getName());
                    }
                    consumer.commitSync();
                }
            } catch (Exception e) {
                System.err.println("Consumer: " + e.getMessage());
                e.printStackTrace();
            }
        });

        System.in.read();
    }
}