package com.signasource.signa_api.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.signasource.signa_api.notification.dto.FcmResult;
import com.signasource.signa_api.notification.dto.PushMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FirebaseService {

    private static final String MASK = "********";

    private final FirebaseMessaging firebaseMessaging;

    public FcmResult sendToTokens(List<String> tokens, PushMessage message) {
        if (tokens == null || tokens.isEmpty()) {
            return FcmResult.of(0, 0, List.of());
        }

        MulticastMessage multicast =
                MulticastMessage.builder()
                        .addAllTokens(tokens)
                        .setNotification(toNotification(message))
                        .putAllData(dataOrEmpty(message))
                        .build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(multicast);
            List<String> invalidTokens = collectStaleTokens(tokens, response);
            log.info(
                    "FCM multicast: {} requested, {} ok, {} failed, {} stale token(s) to purge",
                    tokens.size(),
                    response.getSuccessCount(),
                    response.getFailureCount(),
                    invalidTokens.size());
            return FcmResult.of(
                    response.getSuccessCount(), response.getFailureCount(), invalidTokens);
        } catch (FirebaseMessagingException e) {
            log.error(
                    "FCM multicast to {} token(s) failed entirely (errorCode={})",
                    tokens.size(),
                    e.getMessagingErrorCode(),
                    e);
            return FcmResult.totalFailure();
        }
    }

    public void sendToTopic(String topic, PushMessage message) {
        Message msg =
                Message.builder()
                        .setTopic(topic)
                        .setNotification(toNotification(message))
                        .putAllData(dataOrEmpty(message))
                        .build();

        try {
            String messageId = firebaseMessaging.send(msg);
            log.info("FCM topic '{}' message sent (id={})", topic, messageId);
        } catch (FirebaseMessagingException e) {
            log.error(
                    "FCM message to topic '{}' failed (errorCode={})",
                    topic,
                    e.getMessagingErrorCode(),
                    e);
        }
    }

    public void subscribeToTopic(List<String> tokens, String topic) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        try {
            firebaseMessaging.subscribeToTopic(tokens, topic);
            log.info("Subscribed {} token(s) to FCM topic '{}'", tokens.size(), topic);
        } catch (FirebaseMessagingException e) {
            log.error(
                    "Failed to subscribe {} token(s) to FCM topic '{}' (errorCode={})",
                    tokens.size(),
                    topic,
                    e.getMessagingErrorCode(),
                    e);
        }
    }

    public void unsubscribeFromTopic(List<String> tokens, String topic) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        try {
            firebaseMessaging.unsubscribeFromTopic(tokens, topic);
            log.info("Unsubscribed {} token(s) from FCM topic '{}'", tokens.size(), topic);
        } catch (FirebaseMessagingException e) {
            log.error(
                    "Failed to unsubscribe {} token(s) from FCM topic '{}' (errorCode={})",
                    tokens.size(),
                    topic,
                    e.getMessagingErrorCode(),
                    e);
        }
    }

    private Notification toNotification(PushMessage message) {
        return Notification.builder().setTitle(message.title()).setBody(message.body()).build();
    }

    private Map<String, String> dataOrEmpty(PushMessage message) {
        return message.data() == null ? Map.of() : message.data();
    }

    private List<String> collectStaleTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<String> staleTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = errorCodeOf(sendResponse);
            if (isStaleTokenError(errorCode)) {
                staleTokens.add(tokens.get(i));
            } else {
                log.warn(
                        "FCM rejected token {} with errorCode={}; keeping the token",
                        mask(tokens.get(i)),
                        errorCode);
            }
        }
        return staleTokens;
    }

    private MessagingErrorCode errorCodeOf(SendResponse response) {
        FirebaseMessagingException exception = response.getException();
        return exception == null ? null : exception.getMessagingErrorCode();
    }

    private boolean isStaleTokenError(MessagingErrorCode errorCode) {
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH;
    }

    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return MASK;
        }
        String tail = token.length() <= 4 ? token : token.substring(token.length() - 4);
        return MASK + tail;
    }
}
