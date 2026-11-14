package com.signasource.signa_api.learning.dto;

/**
 * Where a client loads a sign's animation from.
 *
 * <p>A list of these (not a map keyed by meaning): clients convert JSON keys between snake_case and
 * camelCase, which would mangle meanings used as keys.
 *
 * @param meaning echoed exactly as the client asked for it
 * @param url public URL, cache-busted with {@code ?v=} after a re-upload
 * @param format {@code glb} (3D avatar) or {@code mp4} (video)
 * @param lateralUrl side-view video of an {@code mp4} sign; null when there is none
 */
public record SignMediaResponse(String meaning, String url, String format, String lateralUrl) {}
