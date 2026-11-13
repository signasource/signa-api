package com.signasource.signa_api.users.service;

import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A stored profile photo decoded from its data URL, ready to be served as bytes. */
public record AvatarImage(String contentType, byte[] bytes) {

    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/(?:png|jpeg|webp));base64,(.+)$", Pattern.DOTALL);

    static Optional<AvatarImage> parse(String dataUrl) {
        Matcher m = DATA_URL.matcher(dataUrl);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(new AvatarImage(m.group(1), Base64.getDecoder().decode(m.group(2))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
