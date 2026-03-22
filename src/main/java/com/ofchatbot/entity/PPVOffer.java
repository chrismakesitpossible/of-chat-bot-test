package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ppv_offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PPVOffer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long fanId;
    
    /** Price tier index 0–6 (0=free, 1=9.95, 2=29.95, …) for backward compat; primary pricing is price. */
    @Column(nullable = false)
    private Integer level;

    @Column(name = "content_category", length = 50)
    private String contentCategory;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String mediaIds;
    
    @Column(nullable = false)
    private Boolean purchased = false;
    
    @Column(nullable = false)
    private Integer followUpCount = 0;
    
    @Column(nullable = false)
    private LocalDateTime sentAt;
    
    private LocalDateTime purchasedAt;

    /** Script offer (e.g. SO5); when set, on purchase we advance script level. */
    @Column(name = "script_id", length = 50)
    private String scriptId;

    @Column(name = "script_level")
    private Integer scriptLevel;
    
    public LocalDateTime getOfferedAt() {
        return sentAt;
    }
    
    public void setOfferedAt(LocalDateTime offeredAt) {
        this.sentAt = offeredAt;
    }
    
    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
