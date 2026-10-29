package com.moviebot.bot.service;

import com.moviebot.bot.domain.AdminInvite;
import com.moviebot.bot.enums.InviteStatus;
import com.moviebot.bot.repo.AdminInviteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminInviteServiceTest {

    private final AdminInviteRepository repository = mock(AdminInviteRepository.class);
    private final AdminInviteService service = new AdminInviteService(repository);

    private AdminInvite invite(InviteStatus status, LocalDateTime expiresAt) {
        return AdminInvite.builder().token("t").createdByUserId(1L).status(status).expiresAt(expiresAt).build();
    }

    @Test
    void freshPendingInviteIsValid() {
        when(repository.findByToken("t")).thenReturn(Optional.of(invite(InviteStatus.PENDING, LocalDateTime.now().plusMinutes(5))));

        assertThat(service.findValidPendingInvite("t")).isPresent();
    }

    @Test
    void expiredInviteIsRejected() {
        when(repository.findByToken("t")).thenReturn(Optional.of(invite(InviteStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        assertThat(service.findValidPendingInvite("t")).isEmpty();
    }

    @Test
    void alreadyUsedInviteIsRejected() {
        when(repository.findByToken("t")).thenReturn(Optional.of(invite(InviteStatus.AWAITING_CONFIRMATION, LocalDateTime.now().plusMinutes(5))));

        assertThat(service.findValidPendingInvite("t")).isEmpty();
    }
}
