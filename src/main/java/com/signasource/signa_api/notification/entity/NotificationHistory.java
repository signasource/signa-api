package com.signasource.signa_api.notification.entity;

import java.time.Instant;
import java.util.Map;

import com.signasource.signa_api.common.converter.StringMapConverter;
import com.signasource.signa_api.users.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_history", indexes = {
		@Index(name = "idx_notif_hist_user_sent", columnList = "user_id, sent_at"),
		@Index(name = "idx_notif_hist_user_read", columnList = "user_id, is_read"),
		@Index(name = "idx_notif_hist_template", columnList = "template_id")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "template_id", nullable = false)
	private NotificationTemplate template;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(nullable = false)
	private Instant sentAt;

	private Instant readAt;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Convert(converter = StringMapConverter.class)
	@Column(length = 2000)
	private Map<String, String> metadata;
}
