package com.ofchatbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_states")
public class ConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false)
    private String currentState;

    @Column(nullable = false)
    private Integer intensityLevel;

    private String activeFramework;

    private String currentStage;

    @Column(columnDefinition = "TEXT")
    private String fanPreferences;

    @Column(columnDefinition = "TEXT")
    private String conversationContext;

    private Integer messagesSinceLastPurchase;

    private Double totalSpent;

    private LocalDateTime lastEngagementTime;

    private LocalDateTime lastPurchaseTime;

    private Boolean isMonetizationWindowOpen;

    private String lastScriptCategory;

    private Boolean isAwaitingReturn;

    private String anticipationContext;

    private LocalDateTime anticipationSetAt;

    private Integer messageCount;

    private String currentPhase;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public Integer getIntensityLevel() {
        return intensityLevel;
    }

    public void setIntensityLevel(Integer intensityLevel) {
        this.intensityLevel = intensityLevel;
    }

    public String getActiveFramework() {
        return activeFramework;
    }

    public void setActiveFramework(String activeFramework) {
        this.activeFramework = activeFramework;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public String getFanPreferences() {
        return fanPreferences;
    }

    public void setFanPreferences(String fanPreferences) {
        this.fanPreferences = fanPreferences;
    }

    public String getConversationContext() {
        return conversationContext;
    }

    public void setConversationContext(String conversationContext) {
        this.conversationContext = conversationContext;
    }

    public Integer getMessagesSinceLastPurchase() {
        return messagesSinceLastPurchase;
    }

    public void setMessagesSinceLastPurchase(Integer messagesSinceLastPurchase) {
        this.messagesSinceLastPurchase = messagesSinceLastPurchase;
    }

    public Double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(Double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public LocalDateTime getLastEngagementTime() {
        return lastEngagementTime;
    }

    public void setLastEngagementTime(LocalDateTime lastEngagementTime) {
        this.lastEngagementTime = lastEngagementTime;
    }

    public LocalDateTime getLastPurchaseTime() {
        return lastPurchaseTime;
    }

    public void setLastPurchaseTime(LocalDateTime lastPurchaseTime) {
        this.lastPurchaseTime = lastPurchaseTime;
    }

    public Boolean getIsMonetizationWindowOpen() {
        return isMonetizationWindowOpen;
    }

    public void setIsMonetizationWindowOpen(Boolean isMonetizationWindowOpen) {
        this.isMonetizationWindowOpen = isMonetizationWindowOpen;
    }

    public String getLastScriptCategory() {
        return lastScriptCategory;
    }

    public void setLastScriptCategory(String lastScriptCategory) {
        this.lastScriptCategory = lastScriptCategory;
    }

    public Boolean getIsAwaitingReturn() {
        return isAwaitingReturn;
    }

    public void setIsAwaitingReturn(Boolean isAwaitingReturn) {
        this.isAwaitingReturn = isAwaitingReturn;
    }

    public String getAnticipationContext() {
        return anticipationContext;
    }

    public void setAnticipationContext(String anticipationContext) {
        this.anticipationContext = anticipationContext;
    }

    public LocalDateTime getAnticipationSetAt() {
        return anticipationSetAt;
    }

    public void setAnticipationSetAt(LocalDateTime anticipationSetAt) {
        this.anticipationSetAt = anticipationSetAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }
}
