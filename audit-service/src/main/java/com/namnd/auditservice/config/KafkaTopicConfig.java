package com.namnd.auditservice.config;

import com.namnd.kafka.events.topic.KafkaTopics;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

/**
 * Declares audit-events Kafka topic with 3 partitions and 90-day retention.
 */
@Configuration
public class KafkaTopicConfig {

    private static final long NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000;

    @Bean
    public NewTopics auditTopics() {
        return new NewTopics(
                TopicBuilder.name(KafkaTopics.AUDIT_EVENTS)
                        .partitions(3)
                        .replicas(1)
                        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(NINETY_DAYS_MS))
                        .build()
        );
    }
}
