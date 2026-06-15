package com.signasource.signa_api.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@Table(name = "lesson_blocks")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BlockType type;

	@Column(name = "sort_order", nullable = false)
	private int order;

	@Column(columnDefinition = "TEXT")
	private String config;

	@Column(name = "xp_reward", nullable = false)
	private int xpReward;

	@Column(name = "is_exam_eligible", nullable = false)
	private boolean isExamEligible;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lesson_id", nullable = false)
	private Lesson lesson;
}
