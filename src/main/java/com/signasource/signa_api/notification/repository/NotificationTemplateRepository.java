package com.signasource.signa_api.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationTemplate;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

	List<NotificationTemplate> findByCodeAndEnabledTrue(NotificationCode code);

	boolean existsByCode(NotificationCode code);
}
