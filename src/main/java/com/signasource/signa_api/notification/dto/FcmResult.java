package com.signasource.signa_api.notification.dto;

import java.util.List;

public record FcmResult(Status status, int successCount, int failureCount, List<String> invalidTokens) {

	public enum Status {
		SUCCESS, PARTIAL_FAILURE, TOTAL_FAILURE
	}

	public static FcmResult totalFailure() {
		return new FcmResult(Status.TOTAL_FAILURE, 0, 0, List.of());
	}

	public static FcmResult of(int successCount, int failureCount, List<String> invalidTokens) {
		Status status = failureCount == 0 ? Status.SUCCESS : Status.PARTIAL_FAILURE;
		return new FcmResult(status, successCount, failureCount, List.copyOf(invalidTokens));
	}
}
