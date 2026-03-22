package com.namnd.kafka.events.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration for audit interceptor components.
 * Activates only when KafkaTemplate is on classpath (i.e., in services with Kafka).
 * Registers: AuditEventPublisher, AuditAfterCommitListener, AuditAspect, AuditBeanProvider.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class AuditAutoConfiguration {

    @Bean
    public AuditEventPublisher auditEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.application.name:unknown}") String serviceName) {
        return new AuditEventPublisher(kafkaTemplate, serviceName);
    }

    @Bean
    public AuditAfterCommitListener auditAfterCommitListener(AuditEventPublisher publisher) {
        return new AuditAfterCommitListener(publisher);
    }

    @Bean
    public AuditAspect auditAspect(
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:unknown}") String serviceName,
            @Autowired(required = false) Tracer tracer) {
        return new AuditAspect(eventPublisher, objectMapper, serviceName, tracer);
    }

    @Bean
    public AuditBeanProvider auditBeanProvider() {
        return new AuditBeanProvider();
    }
}
