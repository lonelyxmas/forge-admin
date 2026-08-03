package com.mdframe.forge.plugin.message.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceImplTest {

    @Test
    void shouldExposePartialStatusWhenSomeRecipientsFail() {
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 2, 1, 0)).isEqualTo(3);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 2, 0, 1)).isEqualTo(3);
    }

    @Test
    void shouldKeepSuccessAndCompleteFailureStatuses() {
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 2, 0, 0)).isEqualTo(1);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 0, 2, 0)).isEqualTo(2);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 0, 0, 2)).isEqualTo(2);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 0, 1, 2)).isEqualTo(2);
    }
}
