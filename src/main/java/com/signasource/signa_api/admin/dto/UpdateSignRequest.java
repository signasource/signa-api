package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Handedness;

/**
 * Partial update of a sign from the admin panel: a {@code null} field is left as is.
 *
 * @param meaning new name; renaming rewrites every lesson block and learned-sign row that points at
 *     the old meaning (see AdminSignService#update)
 * @param youtubeUrl a blank string clears the stored link
 */
public record UpdateSignRequest(String meaning, Handedness handedness, String youtubeUrl) {}
