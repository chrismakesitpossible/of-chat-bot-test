package com.ofchatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String creatorId;
    
    @Column(nullable = false)
    private String contactId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
    
    @Column(length = 20)
    private String platform = "instagram";
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "external_message_id")
    private String externalMessageId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;
    
    public boolean isFromFan() {
        return "user".equals(role);
    }
    
    public String getContent() {
        return text;
    }
    
    public void setContent(String content) {
        this.text = content;
    }
}
