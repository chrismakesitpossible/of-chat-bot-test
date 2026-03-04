package com.ofchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.Message;
import com.ofchatbot.repository.ConversationStateRepository;
import com.ofchatbot.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScriptEngineService {

    private static final Logger logger = LoggerFactory.getLogger(ScriptEngineService.class);

    @Autowired
    private ConversationStateRepository conversationStateRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ScriptAnalyticsService scriptAnalyticsService;
    
    @Autowired
    private AnthropicService anthropicService;

    private JsonNode frameworks;
    private JsonNode scriptTemplates;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScriptEngineService() {
        loadScriptConfigs();
    }

    private void loadScriptConfigs() {
        try {
            frameworks = objectMapper.readTree(
                new ClassPathResource("scripts/conversation-frameworks.json").getInputStream()
            );
            scriptTemplates = objectMapper.readTree(
                new ClassPathResource("scripts/script-templates.json").getInputStream()
            );
            logger.info("Script configurations loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load script configurations", e);
            throw new RuntimeException("Failed to load script configurations", e);
        }
    }

    public ConversationState getOrCreateConversationState(Conversation conversation, Fan fan) {
        return conversationStateRepository.findByConversationId(conversation.getId())
            .orElseGet(() -> initializeConversationState(conversation, fan));
    }

    private ConversationState initializeConversationState(Conversation conversation, Fan fan) {
        ConversationState state = new ConversationState();
        state.setConversation(conversation);
        state.setCurrentState("CASUAL");
        state.setIntensityLevel(1);
        state.setActiveFramework("SELL");
        state.setCurrentStage("START_CONNECTION");
        state.setMessagesSinceLastPurchase(0);
        state.setTotalSpent(0.0);
        state.setLastEngagementTime(LocalDateTime.now());
        state.setIsMonetizationWindowOpen(false);
        state.setFanPreferences("{}");
        state.setConversationContext("{}");
        return conversationStateRepository.save(state);
    }

    public String analyzeConversationState(ConversationState state, String fanMessage, List<Message> recentMessages) {
        Map<String, Object> analysis = new HashMap<>();
        
        String detectedEmotion = detectEmotionalTone(fanMessage);
        int engagementLevel = calculateEngagementLevel(fanMessage, recentMessages);
        boolean isMonetizationWindow = detectMonetizationWindow(state, fanMessage, engagementLevel);
        String suggestedNextState = determineNextState(state, detectedEmotion, engagementLevel);
        
        Map<String, Object> environmentalContext = anthropicService.analyzeEnvironmentalContext(fanMessage, state);
        boolean isUnavailable = (Boolean) environmentalContext.getOrDefault("is_unavailable", false);
        boolean isReturning = (Boolean) environmentalContext.getOrDefault("is_returning", false);
        String contextReason = (String) environmentalContext.getOrDefault("context_reason", "unknown");
        
        analysis.put("emotion", detectedEmotion);
        analysis.put("engagement_level", engagementLevel);
        analysis.put("is_monetization_window", isMonetizationWindow);
        analysis.put("suggested_next_state", suggestedNextState);
        analysis.put("is_unavailable", isUnavailable);
        analysis.put("is_returning", isReturning);
        analysis.put("context_reason", contextReason);
        
        state.setIsMonetizationWindowOpen(isMonetizationWindow);
        
        return objectMapper.valueToTree(analysis).toString();
    }
    
    private String detectEmotionalTone(String message) {
        return "neutral";
    }

    public void detectAndStoreFanPreferences(ConversationState state, String fanMessage) {
        state.setConversationContext(fanMessage);
        conversationStateRepository.save(state);
    }

    private int calculateEngagementLevel(String fanMessage, List<Message> recentMessages) {
        int score = 0;

        if (fanMessage.length() > 50) score += 2;
        else if (fanMessage.length() > 20) score += 1;

        if (fanMessage.contains("?")) score += 1;

        long emojiCount = fanMessage.chars().filter(c -> c > 0x1F300 && c < 0x1F9FF).count();
        score += Math.min((int) emojiCount, 2);

        if (recentMessages.size() >= 3) {
            long fanMessageCount = recentMessages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();
            if (fanMessageCount >= 2) score += 2;
        }

        return Math.min(score, 10);
    }

    private boolean detectMonetizationWindow(ConversationState state, String fanMessage, int engagementLevel) {
        if (engagementLevel < 5) return false;

        if (state.getMessagesSinceLastPurchase() != null && state.getMessagesSinceLastPurchase() < 3) {
            return false;
        }

        // Use AI to detect interest signals instead of hardcoded patterns
        String analysisPrompt = String.format(
            "Analyze this fan message for interest signals in OnlyFans context.\n\n" +
            "Message: \"%s\"\n\n" +
            "Recent message count: %d\n\n" +
            "Determine if the message shows:\n" +
            "1. Interest in specific content types\n" +
            "2. Desire for more exclusive content\n" +
            "3. Engagement with topics beyond casual chat\n\n" +
            "Return ONLY: true or false",
            fanMessage, 0);
        
        try {
            String aiResult = anthropicService.generateResponse(
                "You are an interest signal detector. Analyze messages and return only 'true' or 'false'.",
                analysisPrompt,
                null
            );
            
            String cleanResult = aiResult.toLowerCase().trim();
            return cleanResult.contains("true") && !cleanResult.contains("false");
            
        } catch (Exception e) {
            logger.error("Failed to analyze interest signals with AI", e);
            return false;
        }
    }

    private String determineNextState(ConversationState state, String emotion, int engagementLevel) {
        String currentState = state.getCurrentState();
        int currentIntensity = state.getIntensityLevel();

        if (emotion.equals("resistant") || emotion.equals("price_concerned")) {
            return currentState;
        }

        if (emotion.equals("hesitant") && currentIntensity > 3) {
            return getPreviousState(currentState);
        }

        if (emotion.equals("aroused") && engagementLevel >= 7) {
            if (currentIntensity < 6) {
                return "EXPLICIT";
            } else {
                return "SEXTING_SESSION";
            }
        }

        if (engagementLevel >= 6 && currentIntensity < 7) {
            return getNextState(currentState);
        }

        if (engagementLevel >= 4 && currentIntensity < 5) {
            return getNextState(currentState);
        }

        return currentState;
    }

    private String getNextState(String currentState) {
        Map<String, String> stateProgression = Map.of(
            "CASUAL", "PLAYFUL",
            "PLAYFUL", "FLIRTY",
            "FLIRTY", "SUGGESTIVE",
            "SUGGESTIVE", "INTIMATE",
            "INTIMATE", "EXPLICIT",
            "EXPLICIT", "SEXTING_SESSION"
        );
        return stateProgression.getOrDefault(currentState, currentState);
    }

    private String getPreviousState(String currentState) {
        Map<String, String> stateRegression = Map.of(
            "SEXTING_SESSION", "EXPLICIT",
            "EXPLICIT", "INTIMATE",
            "INTIMATE", "SUGGESTIVE",
            "SUGGESTIVE", "FLIRTY",
            "FLIRTY", "PLAYFUL",
            "PLAYFUL", "CASUAL"
        );
        return stateRegression.getOrDefault(currentState, currentState);
    }

    public String selectScriptCategory(ConversationState state, String analysisJson) {
        return selectScriptCategory(state, analysisJson, null);
    }

    /** Use recentMessages to avoid WELCOME when there is clear conversation history (e.g. state was reset but fan already chatted). */
    public String selectScriptCategory(ConversationState state, String analysisJson, List<Message> recentMessages) {
            try {
                JsonNode analysis = objectMapper.readTree(analysisJson);
                boolean isMonetizationWindow = analysis.get("is_monetization_window").asBoolean();
                String emotion = analysis.get("emotion").asText();
                boolean isUnavailable = analysis.get("is_unavailable").asBoolean();
                boolean isReturning = analysis.get("is_returning").asBoolean();

                if (state.getLastScriptCategory() == null) {
                    // Only use WELCOME for true first contact. If we have recent back-and-forth or multiple fan messages, stay in flow (ENGAGEMENT).
                    if (recentMessages != null && recentMessages.size() >= 4) {
                        logger.info("lastScriptCategory was null but {} recent messages present; using ENGAGEMENT instead of WELCOME to preserve context", recentMessages.size());
                        return "ENGAGEMENT";
                    }
                    long fanMessageCount = (recentMessages == null) ? 0 : recentMessages.stream().filter(m -> "user".equals(m.getRole())).count();
                    if (fanMessageCount >= 2) {
                        logger.info("lastScriptCategory was null but fan has sent {} messages in recent history; using ENGAGEMENT instead of WELCOME", fanMessageCount);
                        return "ENGAGEMENT";
                    }
                    return "WELCOME";
                }

                if (isReturning && state.getIsAwaitingReturn() != null && state.getIsAwaitingReturn()) {
                    state.setIsAwaitingReturn(false);
                    state.setAnticipationContext(null);
                    conversationStateRepository.save(state);

                    int engagementLevel = analysis.get("engagement_level").asInt();
                    if (engagementLevel >= 7 || state.getIntensityLevel() >= 5) {
                        return "SEXTING_SESSION";
                    } else {
                        return "PPV_OFFER";
                    }
                }

                if (isUnavailable) {
                    state.setIsAwaitingReturn(true);
                    state.setAnticipationContext("Fan indicated unavailability");
                    state.setAnticipationSetAt(LocalDateTime.now());
                    conversationStateRepository.save(state);
                    return "ANTICIPATION_BUILDING";
                }

                if (emotion.equals("resistant") || emotion.equals("price_concerned")) {
                    return "OBJECTION_HANDLERS";
                }

                if (isHighValueFan(state)) {
                    logger.info("High-value fan detected (${} spent), prioritizing VIP treatment", state.getTotalSpent());
                    return "RELATIONSHIP_BUILDING";
                }

                String fanPreferences = state.getFanPreferences();
                if (fanPreferences != null && !fanPreferences.isEmpty()) {
                    String detectedCategory = detectSpecializedCategory(fanPreferences, state);
                    if (detectedCategory != null) {
                        return detectedCategory;
                    }
                }

                if (isMonetizationWindow) {
                    if (state.getCurrentState().equals("SEXTING_SESSION")) {
                        return "SEXTING_SESSION";
                    }

                    if (state.getMessagesSinceLastPurchase() > 5) {
                        return "PPV_OFFER";
                    }
                }

                if (state.getCurrentState().equals("CASUAL") || state.getCurrentState().equals("PLAYFUL")) {
                    if (state.getLastScriptCategory().equals("WELCOME")) {
                        return "FREE_TEASE";
                    }
                    return "ENGAGEMENT";
                }

                if (state.getCurrentState().equals("SEXTING_SESSION")) {
                    return "SEXTING_SESSION";
                }

                Duration timeSinceLastEngagement = state.getLastEngagementTime() != null
                    ? Duration.between(state.getLastEngagementTime(), LocalDateTime.now())
                    : Duration.ofHours(0);
                if (timeSinceLastEngagement.toHours() > 48) {
                    // Don't use RE_ENGAGEMENT if we have an active thread (recent bot + fan messages); fan may be replying to our last message.
                    if (recentMessages != null && recentMessages.size() >= 4) {
                        long botInLastHour = recentMessages.stream()
                            .filter(m -> "bot".equals(m.getRole()))
                            .filter(m -> m.getTimestamp() != null && Duration.between(m.getTimestamp(), LocalDateTime.now()).toHours() <= 1)
                            .count();
                        if (botInLastHour >= 1) {
                            logger.info("48h passed but recent bot message in thread; using ENGAGEMENT instead of RE_ENGAGEMENT");
                            return "ENGAGEMENT";
                        }
                    }
                    return "RE_ENGAGEMENT";
                }

                return "ENGAGEMENT";

            } catch (Exception e) {
                logger.error("Error selecting script category", e);
                return "ENGAGEMENT";
            }
        }

    private String detectSpecializedCategory(String fanPreferences, ConversationState state) {
        try {
            JsonNode prefs = objectMapper.readTree(fanPreferences);

            if (prefs.has("wants_gfe") && prefs.get("wants_gfe").asBoolean()) {
                return "GFE_SCRIPTS";
            }

            if (prefs.has("wants_to_dominate") && prefs.get("wants_to_dominate").asBoolean()) {
                return "SUBMISSIVE_ROLEPLAY";
            }

            if (prefs.has("wants_to_be_dominated") && prefs.get("wants_to_be_dominated").asBoolean()) {
                return "DOMINANT_ROLEPLAY";
            }

            if (prefs.has("specific_fantasy") && !prefs.get("specific_fantasy").asText().isEmpty()) {
                return "FANTASY_FULFILLMENT";
            }

            if (prefs.has("wants_custom") && prefs.get("wants_custom").asBoolean()) {
                return "CUSTOM_OFFER";
            }

        } catch (Exception e) {
            logger.error("Error detecting specialized category", e);
        }

        return null;
    }

    public boolean isHighValueFan(ConversationState state) {
        Double totalSpent = state.getTotalSpent();
        return totalSpent != null && totalSpent >= 100.0;
    }

    public String getScriptTemplate(String category, ConversationState state) {
        try {
            JsonNode categoryNode = scriptTemplates.get("script_categories").get(category);
            if (categoryNode == null) {
                logger.warn("Script category not found: {}", category);
                return null;
            }

            JsonNode strategyNode = categoryNode.get("strategy");
            if (strategyNode != null) {
                return strategyNode.toString();
            }

            return null;
        } catch (Exception e) {
            logger.error("Error getting script strategy", e);
            return null;
        }
    }

    private String determineObjectionType(ConversationState state) {
        if (state.getTotalSpent() != null && state.getTotalSpent() > 50) {
            return "already_spent";
        }
        if (state.getMessagesSinceLastPurchase() != null && state.getMessagesSinceLastPurchase() > 10) {
            return "not_interested";
        }
        return "too_expensive";
    }

    private String determineSextingPhase(ConversationState state) {
        Integer messageCount = state.getMessagesSinceLastPurchase();
        if (messageCount == null) messageCount = 0;

        if (messageCount < 2) return "initiation";
        if (messageCount < 5) return "building_arousal";
        if (messageCount < 8) return "escalation";
        if (messageCount < 12) return "climax";
        if (state.getIsMonetizationWindowOpen()) return "monetization_windows";
        return "conclusion";
    }


    public String getObjectionHandlingGuidance(ConversationState state) {
        try {
            String objectionType = determineObjectionType(state);
            JsonNode strategyNode = scriptTemplates.get("script_categories")
                .get("OBJECTION_HANDLERS")
                .get("strategy")
                .get("objection_types")
                .get(objectionType);

            if (strategyNode != null) {
                return strategyNode.toString();
            }

            return null;
        } catch (Exception e) {
            logger.error("Error getting objection handling guidance", e);
            return null;
        }
    }

    public Map<String, String> getFrameworkGuidance(ConversationState state) {
        Map<String, String> guidance = new HashMap<>();

        try {
            String framework = state.getActiveFramework();
            String stage = state.getCurrentStage();

            JsonNode frameworkNode = frameworks.get("frameworks").get(framework);
            if (frameworkNode == null) {
                return guidance;
            }

            JsonNode stages = frameworkNode.get("stages");
            if (stages != null && stages.isArray()) {
                for (JsonNode stageNode : stages) {
                    if (stageNode.get("stage").asText().equals(stage)) {
                        guidance.put("goal", stageNode.get("goal").asText());
                        guidance.put("exchanges", stageNode.get("exchanges").asText());

                        JsonNode techniques = stageNode.get("techniques");
                        if (techniques != null && techniques.isArray()) {
                            List<String> techniqueList = new ArrayList<>();
                            techniques.forEach(t -> techniqueList.add(t.asText()));
                            guidance.put("techniques", String.join("; ", techniqueList));
                        }

                        JsonNode examples = stageNode.get("examples");
                        if (examples != null && examples.isArray()) {
                            List<String> exampleList = new ArrayList<>();
                            examples.forEach(e -> exampleList.add(e.asText()));
                            guidance.put("examples", String.join(" | ", exampleList));
                        }

                        break;
                    }
                }
            }

            JsonNode stateNode = frameworks.get("conversation_states").get(state.getCurrentState());
            if (stateNode != null) {
                guidance.put("tone", stateNode.get("tone").asText());
                guidance.put("intensity", stateNode.get("intensity").asText());
            }

        } catch (Exception e) {
            logger.error("Error getting framework guidance", e);
        }

        return guidance;
    }

    public void updateConversationState(ConversationState state, String newState, String scriptCategory) {
        state.setCurrentState(newState);
        state.setLastScriptCategory(scriptCategory);
        state.setLastEngagementTime(LocalDateTime.now());

        JsonNode stateNode = frameworks.get("conversation_states").get(newState);
        if (stateNode != null) {
            state.setIntensityLevel(stateNode.get("intensity").asInt());
        }

        if (state.getMessagesSinceLastPurchase() != null) {
            state.setMessagesSinceLastPurchase(state.getMessagesSinceLastPurchase() + 1);
        }

        conversationStateRepository.save(state);
    }

    public void advanceFrameworkStage(ConversationState state) {
        String currentStage = state.getCurrentStage();
        String framework = state.getActiveFramework();

        Map<String, String> sellStageProgression = Map.of(
            "START_CONNECTION", "EXPLORE_NEEDS",
            "EXPLORE_NEEDS", "LEAD_OPPORTUNITY",
            "LEAD_OPPORTUNITY", "LOCK_SALE",
            "LOCK_SALE", "START_CONNECTION"
        );

        if (framework.equals("SELL")) {
            String nextStage = sellStageProgression.getOrDefault(currentStage, currentStage);
            state.setCurrentStage(nextStage);
            conversationStateRepository.save(state);
        }
    }

    public void recordPurchase(ConversationState state, double amount) {
        state.setMessagesSinceLastPurchase(0);
        state.setLastPurchaseTime(LocalDateTime.now());

        Double currentTotal = state.getTotalSpent();
        state.setTotalSpent((currentTotal != null ? currentTotal : 0.0) + amount);

        state.setIsMonetizationWindowOpen(false);

        conversationStateRepository.save(state);

        logger.info("Recorded purchase: ${} for conversation {}", amount, state.getConversation().getId());
    }

}
