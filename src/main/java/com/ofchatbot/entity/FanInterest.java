package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "fan_interests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FanInterest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long fanId;
    
    @Column(nullable = false)
    private String category;
    
    @Column(columnDefinition = "TEXT")
    private String keywords;
    
    @Column(nullable = false)
    private Integer mentionCount = 1;
    
    @Column(nullable = false)
    private LocalDateTime firstMentioned;
    
    @Column(nullable = false)
    private LocalDateTime lastMentioned;
    
    @PrePersist
    protected void onCreate() {
        firstMentioned = LocalDateTime.now();
        lastMentioned = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastMentioned = LocalDateTime.now();
    }
}
