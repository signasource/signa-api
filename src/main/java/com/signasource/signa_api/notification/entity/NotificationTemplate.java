package com.signasource.signa_api.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private NotificationCode code;

    @Column(nullable = false)
    private String defaultTitle;

    @Column(nullable = false, length = 1000)
    private String defaultBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationScope scope;

    @Column(nullable = false)
    private boolean schedulable;

    @Column(nullable = false)
    private boolean enabled;
}
