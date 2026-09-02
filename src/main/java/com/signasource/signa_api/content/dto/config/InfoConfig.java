package com.signasource.signa_api.content.dto.config;

import java.util.List;

public record InfoConfig(String title, String text, List<MythEntry> myths) {
    public record MythEntry(String title, String myth, String reality) {}
}
