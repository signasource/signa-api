package com.signasource.signa_api.learning.event;

import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SignsLearnedEvent extends ApplicationEvent {

    private final transient User user;
    private final List<String> signs;
    private final transient CourseVersion courseVersion;

    public SignsLearnedEvent(
            Object source, User user, List<String> signs, CourseVersion courseVersion) {
        super(source);
        this.user = user;
        this.signs = signs;
        this.courseVersion = courseVersion;
    }
}
