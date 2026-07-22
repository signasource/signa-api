package com.signasource.signa_api.learning.event;

import java.util.UUID;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class XpEarnedEvent extends ApplicationEvent {

    private final UUID userId;
    private final Integer xpAmount;

    public XpEarnedEvent(Object source, UUID userId, Integer xpAmount) {
        super(source);
        this.userId = userId;
        this.xpAmount = xpAmount;
    }
}
