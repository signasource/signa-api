package com.signasource.signa_api.content.exception;

public class CourseAlreadyExistsException extends ContentLoadException {

    public CourseAlreadyExistsException(String code) {
        super("Course already exists: " + code);
    }
}
