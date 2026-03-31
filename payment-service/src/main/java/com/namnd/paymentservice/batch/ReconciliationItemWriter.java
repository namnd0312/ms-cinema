package com.namnd.paymentservice.batch;

import com.namnd.paymentservice.model.ReconciliationItem;
import com.namnd.paymentservice.repository.ReconciliationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Persists reconciliation comparison items to the database in chunks.
 */
@Component
@StepScope
@RequiredArgsConstructor
public class ReconciliationItemWriter implements ItemWriter<ReconciliationItem> {

    private final ReconciliationItemRepository repository;

    @Override
    public void write(Chunk<? extends ReconciliationItem> chunk) {
        repository.saveAll(chunk.getItems());
    }
}
