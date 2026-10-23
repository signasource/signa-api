package com.signasource.signa_api.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // columnDefinition instead of length: it stops Hibernate from generating a CHECK that lists
    // every enum value one by one. ddl-auto=update creates such a constraint but never widens it,
    // so adding a block type would leave existing databases rejecting it. See BlockTypeCheckPatch.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30) not null")
    private BlockType type;

    @Column(name = "\"order\"", nullable = false)
    private int order;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column private Integer xpReward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
