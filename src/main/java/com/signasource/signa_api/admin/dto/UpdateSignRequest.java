package com.signasource.signa_api.admin.dto;

/**
 * Partial update of a sign from the admin panel. A {@code null} or blank {@code youtubeUrl} clears
 * the stored link.
 */
public record UpdateSignRequest(String youtubeUrl) {}
