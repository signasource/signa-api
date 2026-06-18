package com.signasource.signa_api.learning.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "course_versions")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 50)
	private String version;

	@Column
	private Instant publishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VersionStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@OneToMany(mappedBy = "courseVersion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@OrderBy("order ASC")
	@Builder.Default
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private List<Topic> topics = new ArrayList<>();
}
