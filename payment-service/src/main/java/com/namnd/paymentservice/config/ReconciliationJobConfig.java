package com.namnd.paymentservice.config;

import com.namnd.paymentservice.batch.LocalPaymentReader;
import com.namnd.paymentservice.batch.ReconciliationItemWriter;
import com.namnd.paymentservice.batch.ReconciliationJobListener;
import com.namnd.paymentservice.batch.ReconciliationProcessor;
import com.namnd.paymentservice.model.Payment;
import com.namnd.paymentservice.model.ReconciliationItem;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configures the reconciliation Spring Batch job with a single chunk-oriented step.
 * Reader: local payments by date range, Processor: compare vs Stripe, Writer: persist items.
 */
@Configuration
public class ReconciliationJobConfig {

    @Bean
    public Job reconciliationJob(JobRepository jobRepository,
                                 Step reconcileLocalStep,
                                 ReconciliationJobListener listener) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .listener(listener)
                .start(reconcileLocalStep)
                .build();
    }

    @Bean
    public Step reconcileLocalStep(JobRepository jobRepository,
                                   PlatformTransactionManager txManager,
                                   LocalPaymentReader reader,
                                   ReconciliationProcessor processor,
                                   ReconciliationItemWriter writer) {
        return new StepBuilder("reconcileLocalStep", jobRepository)
                .<Payment, ReconciliationItem>chunk(100, txManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
