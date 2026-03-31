package com.namnd.paymentservice.service;

import com.namnd.paymentservice.dto.ReconciliationItemResponse;
import com.namnd.paymentservice.dto.ReconciliationRunResponse;
import com.namnd.paymentservice.exception.PaymentException;
import com.namnd.paymentservice.model.*;
import com.namnd.paymentservice.repository.ReconciliationItemRepository;
import com.namnd.paymentservice.repository.ReconciliationRunRepository;
import com.namnd.paymentservice.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconciliationServiceImpl using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationItemRepository itemRepository;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job reconciliationJob;

    private ReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationServiceImpl(runRepository, itemRepository, jobLauncher, reconciliationJob);
        ReflectionTestUtils.setField(service, "maxDateRangeDays", 31);
    }

    private ReconciliationRun buildRun(Long id, ReconciliationStatus status) {
        ReconciliationRun run = ReconciliationRun.builder()
                .id(id)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 7))
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        return run;
    }

    @Test
    void triggerReconciliation_validRange_createsRunAndLaunchesJob() throws Exception {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 7);

        ReconciliationRun savedRun = buildRun(1L, ReconciliationStatus.RUNNING);
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedRun);

        ReconciliationRunResponse response = service.triggerReconciliation(start, end);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("RUNNING");

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(reconciliationJob), paramsCaptor.capture());

        JobParameters params = paramsCaptor.getValue();
        assertThat(params.getString("startDate")).isEqualTo(start.toString());
        assertThat(params.getString("endDate")).isEqualTo(end.toString());
    }

    @Test
    void triggerReconciliation_invalidRange_throwsException() {
        LocalDate start = LocalDate.of(2024, 1, 10);
        LocalDate end = LocalDate.of(2024, 1, 1); // end before start

        assertThatThrownBy(() -> service.triggerReconciliation(start, end))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("endDate must be >= startDate");
    }

    @Test
    void triggerReconciliation_rangeTooLarge_throwsException() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 4, 1); // 92 days > 31

        assertThatThrownBy(() -> service.triggerReconciliation(start, end))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void getRuns_returnsPaginatedResults() {
        PageRequest pageable = PageRequest.of(0, 10);
        ReconciliationRun run = buildRun(1L, ReconciliationStatus.COMPLETED);
        Page<ReconciliationRun> page = new PageImpl<>(List.of(run), pageable, 1);

        when(runRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

        Page<ReconciliationRunResponse> result = service.getRuns(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    void resolveItem_setsResolvedAndNotes() {
        ReconciliationRun run = buildRun(1L, ReconciliationStatus.COMPLETED);
        ReconciliationItem item = ReconciliationItem.builder()
                .id(5L)
                .run(run)
                .discrepancyType(DiscrepancyType.AMOUNT_MISMATCH)
                .resolved(false)
                .build();

        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(ReconciliationItem.class))).thenReturn(item);

        ReconciliationItemResponse response = service.resolveItem(5L, "Fixed manually");

        assertThat(item.isResolved()).isTrue();
        assertThat(item.getNotes()).isEqualTo("Fixed manually");
        verify(itemRepository).save(item);
    }

    @Test
    void getSummary_noRuns_returnsNull() {
        when(runRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThat(service.getSummary()).isNull();
    }
}
