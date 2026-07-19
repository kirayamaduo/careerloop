package com.group1.career.job;

import com.group1.career.repository.UserRepository;
import com.group1.career.service.AccountDeletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionCleanupJobTest {

    @Test
    @DisplayName("One failed account does not prevent later expired accounts from being purged")
    void failureIsIsolatedPerAccount() {
        UserRepository users = mock(UserRepository.class);
        AccountDeletionService deletionService = mock(AccountDeletionService.class);
        when(users.findExpiredDeletionIds(any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("legacy row"))
                .when(deletionService).hardDeleteExpiredUser(
                        org.mockito.ArgumentMatchers.eq(1L), any(LocalDateTime.class));
        when(deletionService.hardDeleteExpiredUser(
                org.mockito.ArgumentMatchers.eq(2L), any(LocalDateTime.class)))
                .thenReturn(true);

        new AccountDeletionCleanupJob(users, deletionService).run();

        verify(deletionService).hardDeleteExpiredUser(
                org.mockito.ArgumentMatchers.eq(1L), any(LocalDateTime.class));
        verify(deletionService).hardDeleteExpiredUser(
                org.mockito.ArgumentMatchers.eq(2L), any(LocalDateTime.class));
    }
}
