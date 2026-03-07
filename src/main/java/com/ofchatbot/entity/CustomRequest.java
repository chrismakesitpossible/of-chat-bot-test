package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long fanId;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
    
    private Double quotedPrice;
    
    private Integer durationMinutes;
    
    @Column(nullable = false)
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String requirements;
    
    @Column(nullable = false)
    private LocalDateTime requestedAt;
    
    private LocalDateTime quotedAt;
    
    private LocalDateTime approvedAt;
    
    private LocalDateTime completedAt;
    
    /** Amount paid so far toward this custom (e.g. 50% advance). */
    private Double amountPaid;
    
    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        status = "pending";
    }
}
