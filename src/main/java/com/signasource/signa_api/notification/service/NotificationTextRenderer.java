package com.signasource.signa_api.notification.service;

import java.util.Map;

final class NotificationTextRenderer {

	private NotificationTextRenderer() {
	}

	static String render(String text, Map<String, String> data) {
		if (data == null || data.isEmpty()) {
			return text;
		}
		String rendered = text;
		for (Map.Entry<String, String> entry : data.entrySet()) {
			rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		return rendered;
	}
}
