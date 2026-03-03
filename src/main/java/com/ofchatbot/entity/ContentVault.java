package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_vault")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentVault {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String creatorId;
    
    @Column(nullable = false)
    private Long vaultListId;
    
    @Column(nullable = false)
    private Integer level;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private Integer mediaCount;
    
    @Column(nullable = false)
    private Integer photosCount;
    
    @Column(nullable = false)
    private Integer videosCount;

    /** Colour code: SOLO, COUPLE, FULL_SEXTAPE, BUNDLE, CUSTOM, TEASER, GFE_RAPPORT. Derived from folder name. */
    @Column(name = "content_category", length = 50)
    private String contentCategory;

    /** Script-based folder (e.g. S01); level 0-7 parsed from "S01 - Lv.X - ...". */
    @Column(name = "script_id", length = 50)
    private String scriptId;
    
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
}
