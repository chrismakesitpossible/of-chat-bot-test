package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "fans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String creatorId;
    
    @Column(nullable = false, unique = true)
    private String contactId;
    
    private String igUsername;
    
    private String onlyfansUserId;
    
    private String onlyfansUsername;
    
    private String onlyfansChatId;
    
    private Double totalSpending = 0.0;
    
    @Column(nullable = false)
    private Integer messageCount = 0;
    
    @Column(nullable = false)
    private String state = "OPENING";
    
    private String lastIntent = "unknown";
    
    @Column(nullable = false)
    private LocalDateTime lastUpdated = LocalDateTime.now();
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
