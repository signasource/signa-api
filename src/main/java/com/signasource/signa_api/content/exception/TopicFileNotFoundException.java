package com.signasource.signa_api.content.exception;

public class TopicFileNotFoundException extends ContentLoadException {

    public TopicFileNotFoundException(String topicFile, String coursePath) {
        super("Topic file '" + topicFile + "' not found in: " + coursePath);
    }
}
