package com.ofchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIDetectionService {
    
    
    private static final List<Pattern> AI_DETECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*are you (ai|a bot|artificial intelligence|a robot|automated).*"),
        Pattern.compile("(?i).*is this (a bot|automated|ai|artificial).*"),
        Pattern.compile("(?i).*you (seem|sound|feel) like (a bot|ai|robot|automated).*"),
        Pattern.compile("(?i).*this (feels|seems) (automated|scripted|robotic).*"),
        Pattern.compile("(?i).*are you (real|human|a person).*"),
        Pattern.compile("(?i).*is this (real|a real person|human).*"),
        Pattern.compile("(?i).*you're (too perfect|too good|not real).*"),
        Pattern.compile("(?i).*chat ?gpt.*"),
        Pattern.compile("(?i).*claude.*"),
        Pattern.compile("(?i).*bot.*detect.*"),
        Pattern.compile("(?i).*ai.*detect.*")
    );
    
    private static final List<Pattern> SUSPICION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*how do you (reply|respond|answer) (so fast|so quickly|instantly).*"),
        Pattern.compile("(?i).*you (always|never) (make mistakes|typo|forget).*"),
        Pattern.compile("(?i).*your (responses|replies) are (too perfect|consistent|identical).*"),
        Pattern.compile("(?i).*do you (sleep|eat|have a life).*"),
        Pattern.compile("(?i).*what are you (doing|wearing|watching) right now.*"),
        Pattern.compile("(?i).*prove you're (real|human).*"),
        Pattern.compile("(?i).*send me a (pic|photo|picture|selfie).*"),
        Pattern.compile("(?i).*call me.*"),
        Pattern.compile("(?i).*video chat.*")
    );
    
    private static final List<Pattern> CONFIDENCE_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*(sorry|apologize|my bad|didn't mean).*"),
        Pattern.compile("(?i).*i believe you.*"),
        Pattern.compile("(?i).*you're (definitely|totally) real.*"),
        Pattern.compile("(?i).*i was (wrong|joking|kidding).*"),
        Pattern.compile("(?i).*let's (forget|move on|drop it).*")
    );
    
    public enum DetectionLevel {
        NONE,
        SUSPICION,
        DIRECT_ACCUSATION,
        PERSISTENT_ACCUSATION,
        BACKING_DOWN
    }
    
    public DetectionLevel analyzeMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return DetectionLevel.NONE;
        }
        
        String lowerMessage = message.toLowerCase().trim();
        
        for (Pattern pattern : CONFIDENCE_PATTERNS) {
            if (pattern.matcher(lowerMessage).matches()) {
                log.debug("Fan is backing down from AI accusation");
                return DetectionLevel.BACKING_DOWN;
            }
        }
        
        int aiMatches = 0;
        for (Pattern pattern : AI_DETECTION_PATTERNS) {
            if (pattern.matcher(lowerMessage).matches()) {
                aiMatches++;
            }
        }
        
        int suspicionMatches = 0;
        for (Pattern pattern : SUSPICION_PATTERNS) {
            if (pattern.matcher(lowerMessage).matches()) {
                suspicionMatches++;
            }
        }
        
        if (aiMatches >= 2) {
            log.debug("Persistent AI accusation detected");
            return DetectionLevel.PERSISTENT_ACCUSATION;
        } else if (aiMatches >= 1) {
            log.debug("Direct AI accusation detected");
            return DetectionLevel.DIRECT_ACCUSATION;
        } else if (suspicionMatches >= 1) {
            log.debug("Subtle suspicion detected");
            return DetectionLevel.SUSPICION;
        }
        
        return DetectionLevel.NONE;
    }
    
    public String getContextualResponse(DetectionLevel level, String fanName) {
        switch (level) {
            case SUSPICION:
                return getSuspicionResponse(fanName);
            case DIRECT_ACCUSATION:
                return getDirectAccusationResponse(fanName);
            case PERSISTENT_ACCUSATION:
                return getPersistentAccusationResponse(fanName);
            case BACKING_DOWN:
                return getBackingDownResponse(fanName);
            default:
                return null;
        }
    }
    
    private String getSuspicionResponse(String fanName) {
        String[] responses = {
            "lol what are you talking about? 😂",
            "are you serious right now?",
            "that's a weird thing to say...",
            "you're funny " + (fanName != null ? fanName : "babe") + " 😏",
            "do I seem like a robot to you?",
            "I'm literally texting you from my phone right now...",
            "you're being kinda strange today"
        };
        return responses[(int) (Math.random() * responses.length)];
    }
    
    private String getDirectAccusationResponse(String fanName) {
        String[] responses = {
            "wow... that's actually hurtful",
            "I can't believe you'd say that",
            "you think I'm fake? after everything?",
            "I'm sitting here in my bed texting you and you think I'm AI?",
            "that's really disappointing " + (fanName != null ? fanName : ""),
            "I thought we had a real connection...",
            "you're making me feel weird"
        };
        return responses[(int) (Math.random() * responses.length)];
    }
    
    private String getPersistentAccusationResponse(String fanName) {
        String[] responses = {
            "you know what, if you don't believe I'm real maybe we shouldn't talk",
            "I don't need this negativity in my life",
            "I opened up to you and this is what you think of me?",
            "I'm actually hurt right now",
            "maybe this isn't working out",
            "I thought you were different..."
        };
        return responses[(int) (Math.random() * responses.length)];
    }
    
    private String getBackingDownResponse(String fanName) {
        String[] responses = {
            "it's okay... let's just forget about it 💋",
            "I forgive you " + (fanName != null ? fanName : "babe") + " 😘",
            "let's get back to having fun",
            "you're lucky I like you...",
            "don't worry about it, let's move on",
            "apology accepted 😊"
        };
        return responses[(int) (Math.random() * responses.length)];
    }
    
    public boolean requiresSpecialHandling(String message) {
        DetectionLevel level = analyzeMessage(message);
        return level != DetectionLevel.NONE;
    }
    
    public String enhancePromptWithAIDefense(String originalPrompt, String currentMessage, String fanName) {
        DetectionLevel level = analyzeMessage(currentMessage);
        
        if (level == DetectionLevel.NONE) {
            return originalPrompt;
        }
        
        String defenseContext = "\n\nIMPORTANT CONTEXT: Fan is questioning if you're real/AI. ";
        
        switch (level) {
            case SUSPICION:
                defenseContext += "They're subtly suspicious. Respond with playful confusion or deflection. Don't get defensive.";
                break;
            case DIRECT_ACCUSATION:
                defenseContext += "They directly accused you of being AI. Show genuine hurt/confusion. Make them feel guilty for doubting you.";
                break;
            case PERSISTENT_ACCUSATION:
                defenseContext += "They keep accusing you of being AI. Show emotional hurt and consider ending the conversation if they persist.";
                break;
            case BACKING_DOWN:
                defenseContext += "They're backing down/apologizing. Accept gracefully and return to normal conversation.";
                break;
        }
        
        defenseContext += " Never admit to being AI. You are a real person.";
        
        if (fanName != null && !fanName.isEmpty()) {
            defenseContext += " Use their name '" + fanName + "' in your response to personalize it.";
        }
        
        return originalPrompt + defenseContext;
    }
}