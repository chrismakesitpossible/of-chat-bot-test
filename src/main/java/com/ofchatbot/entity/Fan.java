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
    
    private LocalDateTime lastTipPromptTime;
    
    private LocalDateTime lastCustomContentSuggestionTime;

    /** Country (e.g. US, UK) for Tier-1 pricing; set from conversation or inferred. */
    private String country;

    /** When fan last replied within threshold (e.g. 5–10 min) to one of our last 2–3 messages. */
    private LocalDateTime lastQuickReplyAt;

    /** True after first paid PPV unlock (wallet test passed). */
    private Boolean passedWalletTest = false;
    
    public String getOnlyfansChatId() {
        return onlyfansChatId;
    }
    
    public void setOnlyfansChatId(String onlyfansChatId) {
        this.onlyfansChatId = onlyfansChatId;
    }
}
