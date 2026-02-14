package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "script_performance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptPerformance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String creatorId;
    
    @Column(nullable = false)
    private String scriptCategory;
    
    @Column(nullable = false)
    private String conversationState;
    
    @Column(nullable = false)
    private Integer timesUsed = 0;
    
    @Column(nullable = false)
    private Integer responsesReceived = 0;
    
    @Column(nullable = false)
    private Integer purchasesGenerated = 0;
    
    @Column(nullable = false)
    private Double totalRevenueGenerated = 0.0;
    
    @Column(nullable = false)
    private Double averageEngagementScore = 0.0;
    
    @Column(nullable = false)
    private Double conversionRate = 0.0;
    
    @Column(nullable = false)
    private Double averageRevenuePerUse = 0.0;
    
    @Column(nullable = false)
    private Integer positiveResponses = 0;
    
    @Column(nullable = false)
    private Integer neutralResponses = 0;
    
    @Column(nullable = false)
    private Integer negativeResponses = 0;
    
    private LocalDateTime lastUsedAt;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void incrementUsage() {
        this.timesUsed++;
        this.lastUsedAt = LocalDateTime.now();
        recalculateMetrics();
    }
    
    public void recordResponse(int engagementScore) {
        this.responsesReceived++;
        
        double currentTotal = this.averageEngagementScore * (this.responsesReceived - 1);
        this.averageEngagementScore = (currentTotal + engagementScore) / this.responsesReceived;
        
        if (engagementScore >= 7) {
            this.positiveResponses++;
        } else if (engagementScore >= 4) {
            this.neutralResponses++;
        } else {
            this.negativeResponses++;
        }
        
        recalculateMetrics();
    }
    
    public void recordPurchase(double amount) {
        this.purchasesGenerated++;
        this.totalRevenueGenerated += amount;
        recalculateMetrics();
    }
    
    private void recalculateMetrics() {
        if (this.timesUsed > 0) {
            this.conversionRate = (double) this.purchasesGenerated / this.timesUsed * 100;
            this.averageRevenuePerUse = this.totalRevenueGenerated / this.timesUsed;
        }
    }
}
