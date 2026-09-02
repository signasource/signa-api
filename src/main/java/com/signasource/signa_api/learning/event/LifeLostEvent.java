package com.signasource.signa_api.learning.event;

import com.signasource.signa_api.users.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LifeLostEvent extends ApplicationEvent {

    private final transient User user;

    public LifeLostEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
