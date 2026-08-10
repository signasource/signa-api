package com.signasource.signa_api.content.dto.validation;

public record ValidationContext(String topicCode, String lessonCode, int blockIndex) {

    public String location() {
        return "Topic " + topicCode + " > Lesson " + lessonCode + " > Block #" + blockIndex;
    }
}
