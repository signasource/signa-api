package com.signasource.signa_api.content.validator.block;

public record ValidationContext(String topicCode, String lessonCode, int blockIndex) {

    public String location() {
        return "Topic " + topicCode + " > Lesson " + lessonCode + " > Block #" + blockIndex;
    }
}
