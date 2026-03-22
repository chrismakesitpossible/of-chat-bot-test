package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per fan per script (e.g. SO5 Solo).
 * When completed_at is set, do not offer that script again (never run script twice).
 */
@Entity
@Table(name = "fan_script_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"fan_id", "script_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FanScriptProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fanId;

    @Column(name = "script_id", nullable = false, length = 50)
    private String scriptId;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** When we last sent a script PPV offer at this level (to avoid repeating same offer within cooldown). */
    @Column(name = "last_shower_offer_sent_at")
    private LocalDateTime lastOfferSentAt;

    /** How many times we've sent a PPV at the current level without the fan purchasing. Max 2 before abandoning (Issue #20). */
    @Column(name = "attempts_at_current_level")
    private Integer attemptsAtCurrentLevel = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
