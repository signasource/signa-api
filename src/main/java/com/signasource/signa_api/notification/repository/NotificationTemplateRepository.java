package com.signasource.signa_api.notification.repository;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    List<NotificationTemplate> findByCodeAndEnabledTrue(NotificationCode code);

    boolean existsByCode(NotificationCode code);

    boolean existsByCodeAndDefaultTitle(NotificationCode code, String defaultTitle);
}
