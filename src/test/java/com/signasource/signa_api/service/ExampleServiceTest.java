package com.signasource.signa_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ExampleServiceTest {
	private final ExampleService mathService = new ExampleService();

	@Test
	void shouldSumTwoNumbers() {
		int result = mathService.sum(2, 3);
		assertEquals(5, result);
	}
}
