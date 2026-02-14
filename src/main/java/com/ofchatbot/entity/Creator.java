package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "creators")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Creator {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String creatorId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String tone;
    
    @Column(nullable = false)
    private String ofUrl;
    
    private String trackingCode;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
