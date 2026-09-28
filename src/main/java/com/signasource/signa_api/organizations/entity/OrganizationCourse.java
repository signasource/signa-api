package com.signasource.signa_api.organizations.entity;

import com.signasource.signa_api.learning.entity.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A course the organization contracted; every active member gets access to it. */
@Data
@Entity
@Table(
        name = "organization_courses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "course_id"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Course course;

    @Column(nullable = false, updatable = false)
    private Instant contractedAt;

    @PrePersist
    protected void onCreate() {
        this.contractedAt = Instant.now();
    }
}
