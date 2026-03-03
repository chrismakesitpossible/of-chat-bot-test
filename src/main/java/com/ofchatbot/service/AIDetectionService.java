package com.ofchatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIDetectionService {
    
    private final AnthropicService anthropicService;
    
    public enum DetectionLevel {
        NONE, SUSPICION, DIRECT_ACCUSATION, PERSISTENT_ACCUSATION, BACKING_DOWN
    }
    
    public DetectionLevel analyzeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return DetectionLevel.NONE;
        }
        
        // Use AI to determine if user is questioning authenticity
        String analysisPrompt = String.format(
            "Analyze this message for AI detection in OnlyFans context.\n\n" +
            "Message: \"%s\"\n\n" +
            "Determine if the user is:\n" +
            "1. Directly asking if you're AI/bot/automated\n" +
            "2. Expressing suspicion about authenticity\n" +
            "3. Backing down from conversation\n" +
            "4. Persistently accusing you of being AI\n\n" +
            "Return ONLY: NONE, SUSPICION, DIRECT_ACCUSATION, PERSISTENT_ACCUSATION, or BACKING_DOWN",
            message);
        
        try {
            String aiResult = anthropicService.generateResponse(
                "You are an AI detection analyzer. Analyze messages and return only one word: NONE, SUSPICION, DIRECT_ACCUSATION, PERSISTENT_ACCUSATION, or BACKING_DOWN.",
                analysisPrompt,
                null
            );
            
            String cleanResult = aiResult.toUpperCase().trim();
            
            try {
                return DetectionLevel.valueOf(cleanResult);
            } catch (IllegalArgumentException e) {
                log.debug("AI detection result unclear: {}", cleanResult);
                return DetectionLevel.SUSPICION;
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze AI detection with AI", e);
            return DetectionLevel.SUSPICION;
        }
    }
    
    public boolean requiresSpecialHandling(String message) {
        DetectionLevel level = analyzeMessage(message);
        return level != DetectionLevel.NONE;
    }
    
    public String getContextualResponse(DetectionLevel level, String fanName) {
        if (level == DetectionLevel.NONE) {
            return null; // No special handling needed
        }
        
        // Use AI to generate appropriate defensive response
        String responsePrompt = String.format(
            "You are a flirty OnlyFans creator responding to AI accusations.\n\n" +
            "Detection Level: %s\n" +
            "Fan Name: %s\n\n" +
            "Generate a natural, defensive response that:\n" +
            "1. Shows genuine emotion (hurt, confusion, annoyance)\n" +
            "2. Never admits to being AI\n" +
            "3. May playfully deflect or express disappointment\n" +
            "4. Sounds like a real person, not a bot\n" +
            "5. Keep it short and conversational\n\n" +
            "Respond with ONLY the message text, no explanations.",
            level.toString(),
            fanName != null ? "babe" : fanName
        );
        
        try {
            return anthropicService.generateResponse(
                "You are a defensive response generator. Generate only the actual response message.",
                responsePrompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate defensive response", e);
            return "lol what are you talking about? 😂";
        }
    }
}