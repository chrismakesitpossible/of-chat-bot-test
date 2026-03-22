package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "vault_media")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VaultMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contentVaultId;

    @Column(nullable = false)
    private String mediaId;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String categories;

    private Integer duration;

    /** Media filename/caption from the vault (e.g. "L3 - tank top up, chest reveal"). */
    @Column(columnDefinition = "TEXT")
    private String name;

    /** Script level parsed from the media name prefix (L1=1, L2=2, ... L7=7). Null if not a script media item. */
    private Integer level;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

