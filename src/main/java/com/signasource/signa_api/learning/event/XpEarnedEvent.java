package com.signasource.signa_api.learning.event;

import com.signasource.signa_api.users.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class XpEarnedEvent extends ApplicationEvent {

    private final User user;
    private final Integer xpAmount;

    public XpEarnedEvent(Object source, User user, Integer xpAmount) {
        super(source);
        this.user = user;
        this.xpAmount = xpAmount;
    }
}
