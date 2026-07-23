package com.signasource.signa_api.content.exception;

public class CourseFileNotFoundException extends ContentLoadException {

    public CourseFileNotFoundException(String path) {
        super("Missing course.yml: " + path);
    }
}
