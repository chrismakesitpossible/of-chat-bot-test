package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "fan_purchases")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FanPurchase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long fanId;
    
    @Column(nullable = false)
    private String mediaId;
    
    @Column(nullable = false)
    private Integer level;
    
    @Column(nullable = false)
    private Double pricePaid;
    
    @Column(nullable = false)
    private LocalDateTime purchasedAt;
    
    @PrePersist
    protected void onCreate() {
        purchasedAt = LocalDateTime.now();
    }
}
