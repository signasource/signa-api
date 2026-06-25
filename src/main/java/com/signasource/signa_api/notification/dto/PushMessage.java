package com.signasource.signa_api.notification.dto;

import java.util.Map;

public record PushMessage(String title, String body, Map<String, String> data) {
}
