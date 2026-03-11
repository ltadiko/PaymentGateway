package com.fintech.gateway.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka serialization configuration for Spring Boot 4 + Jackson.
 *
 * <p>Spring Kafka's {@link JsonSerializer}/{@link JsonDeserializer} use
 * Jackson 2 internally. This configuration ensures the JSR310 module
 * is available for {@code java.time.Instant} serialization by using
 * the default ObjectMapper created by Spring Kafka's serializers,
 * which auto-discovers the module from the classpath.
 *
 * <p>Also wires the {@link CommonErrorHandler} from {@link KafkaConsumerConfig}
 * into the listener container factory for retry + DLT support.
 */
@Configuration
public class KafkaSerializationConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:payment-gateway}")
    private String groupId;

    /**
     * Creates a producer factory with JSON serialization.
     *
     * @return configured producer factory
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a KafkaTemplate using the configured producer factory.
     *
     * @param producerFactory the producer factory
     * @return configured Kafka template
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Creates a consumer factory with JSON deserialization.
     *
     * @return configured consumer factory
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.fintech.gateway.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates the Kafka listener container factory with error handling.
     *
     * @param consumerFactory the consumer factory
     * @param errorHandler    the error handler with DLT support
     * @return configured listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
