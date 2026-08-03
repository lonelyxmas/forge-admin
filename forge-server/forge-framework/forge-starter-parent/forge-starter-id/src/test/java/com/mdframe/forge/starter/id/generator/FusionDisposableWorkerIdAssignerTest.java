package com.mdframe.forge.starter.id.generator;

import com.mdframe.forge.starter.id.service.IWorkNodePOAtomicService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class FusionDisposableWorkerIdAssignerTest {

    @Test
    void shouldReturnWorkerIdWithinConfiguredCapacity() {
        FusionDisposableWorkerIdAssigner assigner = assignerReturning(7L, 3);

        assertThat(assigner.assignWorkerId()).isEqualTo(7L);
    }

    @Test
    void shouldFailClosedWhenDatabaseSequenceExceedsWorkerBits() {
        FusionDisposableWorkerIdAssigner assigner = assignerReturning(8L, 3);

        assertThatThrownBy(assigner::assignWorkerId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("容量上限 7");
    }

    private FusionDisposableWorkerIdAssigner assignerReturning(long workerId, int workerBits) {
        IWorkNodePOAtomicService service = mock(IWorkNodePOAtomicService.class);
        doAnswer(invocation -> {
            invocation.<com.mdframe.forge.starter.id.entity.WorkerNodePO>getArgument(0)
                    .setWorkNodeId(workerId);
            return null;
        }).when(service).addWorkerNode(any());
        return new FusionDisposableWorkerIdAssigner(service, workerBits);
    }
}
