package com.signasource.signa_api.learning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "topics")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 50)
	private String code;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private int order;

	@Column
	private String coverUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_version_id", nullable = false)
	private CourseVersion courseVersion;

	@OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@Builder.Default
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private List<Lesson> lessons = new ArrayList<>();
}
