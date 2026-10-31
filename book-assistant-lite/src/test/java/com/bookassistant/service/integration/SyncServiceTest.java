package com.bookassistant.service.integration;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.ExternalNoteRepository;
import com.bookassistant.repository.LinkedAccountRepository;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private LinkedAccountRepository linkedAccountRepository;

    @Mock
    private ExternalNoteRepository externalNoteRepository;

    @Mock
    private List<ExternalPlatformAdapter> adapters;

    @InjectMocks
    private SyncService syncService;

    @Test
    void testSyncUserNotes_NoAccounts() {
        when(linkedAccountRepository.findByUserId(anyLong())).thenReturn(Collections.emptyList());

        int result = syncService.syncUserNotes(1L);

        assertEquals(0, result);
    }
}


