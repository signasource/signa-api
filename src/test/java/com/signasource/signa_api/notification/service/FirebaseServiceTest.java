package com.signasource.signa_api.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.signasource.signa_api.notification.dto.FcmResult;
import com.signasource.signa_api.notification.dto.PushMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseServiceTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FirebaseService firebaseService = new FirebaseService(messaging);
    private final PushMessage message = new PushMessage("title", "body", Map.of("k", "v"));

    @Test
    void sendToTokensReturnsSuccessWhenNoTokens() {
        FcmResult result = firebaseService.sendToTokens(List.of(), message);

        assertEquals(FcmResult.Status.SUCCESS, result.status());
        assertEquals(0, result.successCount());
        assertTrue(result.invalidTokens().isEmpty());
        verifyNoInteractions(messaging);
    }

    @Test
    void sendToTokensCollectsStaleTokens() throws Exception {
        BatchResponse batch = mock(BatchResponse.class);
        SendResponse ok = mock(SendResponse.class);
        SendResponse failed = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);

        when(ok.isSuccessful()).thenReturn(true);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(batch.getSuccessCount()).thenReturn(1);
        when(batch.getFailureCount()).thenReturn(1);
        when(batch.getResponses()).thenReturn(List.of(ok, failed));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        FcmResult result = firebaseService.sendToTokens(List.of("good", "bad"), message);

        assertEquals(FcmResult.Status.PARTIAL_FAILURE, result.status());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(List.of("bad"), result.invalidTokens());
    }

    @Test
    void sendToTokensDoesNotPurgeOnInvalidArgument() throws Exception {
        BatchResponse batch = mock(BatchResponse.class);
        SendResponse failed = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);

        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(batch.getSuccessCount()).thenReturn(0);
        when(batch.getFailureCount()).thenReturn(1);
        when(batch.getResponses()).thenReturn(List.of(failed));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        FcmResult result = firebaseService.sendToTokens(List.of("maybe-bad"), message);

        assertEquals(FcmResult.Status.PARTIAL_FAILURE, result.status());
        assertEquals(1, result.failureCount());
        assertTrue(result.invalidTokens().isEmpty());
    }

    @Test
    void sendToTokensReturnsTotalFailureOnException() throws Exception {
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(mock(FirebaseMessagingException.class));

        FcmResult result = firebaseService.sendToTokens(List.of("t"), message);

        assertEquals(FcmResult.Status.TOTAL_FAILURE, result.status());
        assertEquals(0, result.successCount());
        assertTrue(result.invalidTokens().isEmpty());
    }

    @Test
    void sendToTopicSendsMessage() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("message-id");

        firebaseService.sendToTopic("topic", message);

        verify(messaging).send(any(Message.class));
    }

    @Test
    void sendToTopicSwallowsException() throws Exception {
        when(messaging.send(any(Message.class))).thenThrow(mock(FirebaseMessagingException.class));

        assertDoesNotThrow(() -> firebaseService.sendToTopic("topic", message));
    }

    @Test
    void subscribeAndUnsubscribeSkipWhenEmpty() {
        firebaseService.subscribeToTopic(List.of(), "topic");
        firebaseService.unsubscribeFromTopic(List.of(), "topic");

        verifyNoInteractions(messaging);
    }

    @Test
    void subscribeAndUnsubscribeCallFirebase() throws Exception {
        firebaseService.subscribeToTopic(List.of("t"), "topic");
        firebaseService.unsubscribeFromTopic(List.of("t"), "topic");

        verify(messaging).subscribeToTopic(List.of("t"), "topic");
        verify(messaging).unsubscribeFromTopic(List.of("t"), "topic");
    }

    @Test
    void subscribeAndUnsubscribeSwallowExceptions() throws Exception {
        when(messaging.subscribeToTopic(any(), any()))
                .thenThrow(mock(FirebaseMessagingException.class));
        when(messaging.unsubscribeFromTopic(any(), any()))
                .thenThrow(mock(FirebaseMessagingException.class));

        assertDoesNotThrow(() -> firebaseService.subscribeToTopic(List.of("t"), "topic"));
        assertDoesNotThrow(() -> firebaseService.unsubscribeFromTopic(List.of("t"), "topic"));
    }
}
