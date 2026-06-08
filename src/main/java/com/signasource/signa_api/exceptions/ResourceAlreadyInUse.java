package com.signasource.signa_api.exceptions;

public class ResourceAlreadyInUse extends RuntimeException {
	public ResourceAlreadyInUse(String message) {
		super(message);
	}
}
