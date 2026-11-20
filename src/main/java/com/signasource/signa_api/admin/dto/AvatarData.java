package com.signasource.signa_api.admin.dto;

/** Shape of a profile photo accepted by the admin endpoints: a small base64 image data URL. */
final class AvatarData {

    /** Empty string means "no photo" (it clears an existing one on update). */
    static final String PATTERN = "^$|^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$";

    static final int MAX_LENGTH = 400_000;

    private AvatarData() {}
}
