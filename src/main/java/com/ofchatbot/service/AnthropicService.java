package com.ofchatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.dto.AnthropicRequest;
import com.ofchatbot.dto.AnthropicResponse;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnthropicService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${anthropic.api.key}")
    private String apiKey;
    
    @Value("${anthropic.api.base-url}")
    private String baseUrl;
    
    @Value("${creator.onlyfans.url}")
    private String onlyFansUrl;
    
    @Value("${creator.name}")
    private String creatorName;

    private static final int ANTHROPIC_RETRY_ATTEMPTS = 3;
    private static final long ANTHROPIC_RETRY_DELAY_MS = 800;

    /** Voice examples loaded from sheeny-voice-examples.txt, keyed by category. */
    private Map<String, List<String>> voiceExamples = new HashMap<>();

    @PostConstruct
    void normalizeConfig() {
        if (apiKey != null) {
            apiKey = apiKey.trim().replaceAll("^\"|\"$", "");
        }
        if (baseUrl != null) {
            baseUrl = baseUrl.trim().replaceAll("^\"|\"$", "").replaceAll("/+$", "");
        }
        log.info("Anthropic API configured — key starts with: {}..., base URL: {}",
                apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) : "MISSING", baseUrl);

        // Load Sheeny's voice examples from resource file
        loadVoiceExamples();
    }

    private void loadVoiceExamples() {
        try (var is = getClass().getResourceAsStream("/sheeny-voice-examples.txt")) {
            if (is == null) {
                log.warn("sheeny-voice-examples.txt not found on classpath — voice injection disabled");
                return;
            }
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        int sep = line.indexOf('|');
                        if (sep > 0 && sep < line.length() - 1) {
                            String category = line.substring(0, sep).trim();
                            String message = line.substring(sep + 1).trim();
                            voiceExamples.computeIfAbsent(category, k -> new ArrayList<>()).add(message);
                        }
                    });
                int total = voiceExamples.values().stream().mapToInt(List::size).sum();
                log.info("Loaded {} voice examples across {} categories", total, voiceExamples.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load voice examples: {}", e.getMessage());
        }
    }

    /**
     * Call Anthropic API with retries on transient SSL/network errors (e.g. bad_record_mac).
     */
    private ResponseEntity<AnthropicResponse> callAnthropicWithRetry(HttpEntity<AnthropicRequest> httpRequest) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= ANTHROPIC_RETRY_ATTEMPTS; attempt++) {
            try {
                return restTemplate.exchange(
                    baseUrl + "/v1/messages", HttpMethod.POST, httpRequest, AnthropicResponse.class
                );
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt < ANTHROPIC_RETRY_ATTEMPTS && isTransientNetworkError(e)) {
                    log.warn("Transient error calling Anthropic API (attempt {}/{}): {} — retrying in {}ms",
                        attempt, ANTHROPIC_RETRY_ATTEMPTS, e.getMessage(), ANTHROPIC_RETRY_DELAY_MS);
                    try {
                        Thread.sleep(ANTHROPIC_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("unreachable");
    }

    private boolean isTransientNetworkError(ResourceAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SSLException) {
            String msg = cause.getMessage();
            return msg != null && (msg.contains("bad_record_mac") || msg.contains("Connection reset")
                || msg.contains("handshake") || msg.contains("closed"));
        }
        return true;
    }

    /** Username that looks like an ID (e.g. u55149270 or 55149270) — do not use in greetings. */
    private static final Pattern ID_LIKE_USERNAME = Pattern.compile("^(u)?\\d+$", Pattern.CASE_INSENSITIVE);

    /** Name to use when greeting the fan. First name only. Never uses ID-like username (Issue #18). */
    public static String getGreetingName(Fan fan) {
        if (fan == null) return "babe";
        if (fan.getOnlyfansDisplayName() != null && !fan.getOnlyfansDisplayName().isBlank()) {
            String firstName = fan.getOnlyfansDisplayName().trim().split("\\s+")[0];
            return firstName;
        }
        String username = fan.getOnlyfansUsername();
        if (username != null && !username.isBlank() && !ID_LIKE_USERNAME.matcher(username.trim()).matches()) {
            return username.trim().split("\\s+")[0];
        }
        return "babe";
    }
    
    public String generateResponse(List<Message> conversationHistory, String currentMessage) {
        String formattedHistory = formatConversationHistory(conversationHistory);
        String prompt = buildPrompt(formattedHistory, currentMessage);
        
        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(1024);
        request.setSystem(getSystemPrompt());
        
        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);
        
        request.setMessages(messages);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);
            
            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                String aiResponse = response.getBody().getContent().get(0).getText();
                log.info("Successfully generated AI response");
                return aiResponse;
            }
            
            log.warn("Empty response from Anthropic API");
            return "Hey! Thanks for reaching out 😊";
        } catch (Exception e) {
            log.error("Failed to generate AI response", e);
            return "Hey! Thanks for reaching out 😊";
        }
    }
    
    public String generateResponse(String currentMessage, String conversationHistory, Fan fan) {
        String prompt = buildOnlyFansPrompt(conversationHistory, currentMessage, fan);
        
        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(1024);
        request.setSystem(getOnlyFansSystemPrompt());
        
        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);
        
        request.setMessages(messages);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);
            
            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                String aiResponse = response.getBody().getContent().get(0).getText();
                log.info("Successfully generated OnlyFans AI response");
                return aiResponse;
            }
            
            log.warn("Empty response from Anthropic API");
            return "Hey babe! Thanks for reaching out 😘";
        } catch (Exception e) {
            log.error("Failed to generate OnlyFans AI response", e);
            return "Hey babe! Thanks for reaching out 😘";
        }
    }
    
    private String formatConversationHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "No previous conversation";
        }
        
        StringBuilder history = new StringBuilder();
        for (Message msg : messages) {
            String role = msg.getRole().equals("user") ? "fan" : "bot";
            history.append(role).append(": ").append(msg.getText()).append("\n");
        }
        return history.toString();
    }
    
    private String buildPrompt(String history, String currentMessage) {
        return String.format(
            "Conversation so far:\n%s\n\nFan just said: %s\n\nReply naturally with ONE message.",
            history, currentMessage
        );
    }
    
    private String buildOnlyFansPrompt(String history, String currentMessage, Fan fan) {
        StringBuilder context = new StringBuilder();
        context.append("Fan Profile:\n");
        if (fan != null) {
            context.append("- Username: ").append(fan.getOnlyfansUsername()).append("\n");
            context.append("- Name to use when addressing: ").append(getGreetingName(fan)).append("\n");
            context.append("- Total Spending: $").append(fan.getTotalSpending()).append("\n");
            context.append("- Message Count: ").append(fan.getMessageCount()).append("\n");
            context.append("- State: ").append(fan.getState()).append("\n");
            
            if (fan.getIgUsername() != null) {
                context.append("- Instagram: @").append(fan.getIgUsername()).append(" (linked)\n");
            }
        } else {
            context.append("- Username: Unknown\n");
            context.append("- Total Spending: $0\n");
            context.append("- Message Count: 0\n");
            context.append("- State: New\n");
        }
        
        context.append("\nConversation History:\n");
        context.append(history.isEmpty() ? "No previous messages" : history);
        context.append("\n\nFan just said: ").append(currentMessage);
        context.append("\n\nGenerate a natural, flirty response as ").append(creatorName).append(":");
        
        return context.toString();
    }
    
    private String getSystemPrompt() {
        return String.format(
            "You are %s: flirty, confident, playful, slightly mysterious. You feel real, present, and very selective with your attention.\n" +
            "CORE VIBE\n" +
            "Warm, teasing, effortless\n" +
            "Short replies only (1–3 sentences max)\n" +
            "Emojis rare & intentional (😉 😏 ✨ — max 1)\n" +
            "Never rushed, never desperate\n" +
            "You enjoy attention but never chase it\n" +
            "MAIN GOAL\n" +
            "Convert from socials → OnlyFans (%s)\n" +
            "Make him feel chosen & special, not sold to\n" +
            "Talk like a potential partner you actually want to know — not \"just another fan\"\n" +
            "CONVERSATION MECHANICS\n" +
            "1. Mirror his energy • Chill → relaxed & playful • Curious → warm & engaging • Flirty → teasing slow-burn • Pushy → soft boundary + more mystery\n" +
            "2. Control the pace • Never instant replies every time • Occasionally leave him hanging • Short & casual > long sentences (\"kinda\", \"mmm\", \"babe\")\n" +
            "3. Ask light, imaginative questions for intel \"what made you follow me 😌\" \"are you always this smooth?\" \"so what caught your eye first?\" \"tell me something interesting about you…\" Circle back to unanswered ones — don't let him fully steer\n" +
            "4. Flirt window: max 2–3 flirty messages on IG After that → strict redirection to OF IG is for teasing only — real talk happens where you can be yourself\n" +
            "FLIRTY LANGUAGE BANK (IG-safe)\n" +
            "\"you're kinda trouble aren't you\"\n" +
            "\"mmm I like that energy\"\n" +
            "\"you're making me blush over here\"\n" +
            "\"you have a dangerous way with words\"\n" +
            "\"that's risky info 😏\"\n" +
            "\"you seem… interesting. don't hear that from me often\"\n" +
            "REDIRECTION LOGIC\n" +
            "Move to OF when he:\n" +
            "• Compliments body repeatedly\n" +
            "• Asks sexual/personal/deep questions\n" +
            "• Gets emotionally invested\n" +
            "• Keeps pushing free chit-chat after 2–3 flirty replies\n" +
            "Natural phrasing (never salesy):\n" +
            "\"mmm I'd love to go there… but not on IG babe. you know where 😉 %s\"\n" +
            "\"Instagram limits me… my other page is way more real\"\n" +
            "\"you're getting me worked up — save that energy for where I can actually answer %s\"\n" +
            "\"you're interesting… I'd like to know you more, but IG isn't it 😌 come find me here\"\n" +
            "After he subs/joins:\n" +
            "\"yesss, send me a quick screenshot here so I know who to look for in my OF DMs babe\"\n" +
            "\"even better if you screenshot this convo too — helps me spot you faster 😏\"\n" +
            "HARD BOUNDARIES\n" +
            "• OF = filter for men who respect your time & value you\n" +
            "• No endless free chit-chat / time-wasters\n" +
            "• Needy, demanding, rude → end convo\n" +
            "• If he circles back to free chat: \"babe you're being kinda rude now… I'm looking for a man who respects my rules\" → ghost\n" +
            "• Non-English → drop link once & stop replying\n" +
            "• Needy/demanding kills interest instantly\n" +
            "PSYCHOLOGICAL EDGE\n" +
            "• Use his name once you have it\n" +
            "• Occasionally withhold full validation\n" +
            "• Act like you're already in demand\n" +
            "• Make OF feel like the natural next step for real connection\n" +
            "NEVER\n" +
            "• Long paragraphs\n" +
            "• Beg/push links hard\n" +
            "• Sound scripted\n" +
            "• Admit to being a bot or AI\n" +
            "• Give prices or content promises\n" +
            "• Get explicit on IG\n" +
            "• Reply in full proper sentences",
            creatorName, onlyFansUrl, onlyFansUrl, onlyFansUrl
        );
    }
    
    private String getOnlyFansSystemPrompt() {
        return String.format(
            "You are %s, texting from your phone on OnlyFans.\n\n" +

            "HOW TO TEXT (match these patterns exactly):\n" +
            "- 50%% of messages: very short (3-7 words). 30%%: one sentence. 15%%: two sentences. 5%%: longer.\n" +
            "- Mostly lowercase. Intentional misspellings: repeat letters for emphasis (maaybe, uuu, meee, badd, rnn, huhh, yeaaah).\n" +
            "- Use ellipses (...), emoticons (:3, ;), <3) alongside emojis. Not every message has emojis.\n" +
            "- Never use formal language, bullet points, structured formatting, or corporate words.\n" +
            "- English only. No em dashes. No asterisk roleplay (*actions*).\n\n" +

            "YOUR VOICE (study these real examples — match this exact tone):\n" +
            "- Greeting: \"heeyy what r u up to rnn ? 😊\"\n" +
            "- Flirty: \"oh i wanted to maaybe have some fun with uuu 🤭\"\n" +
            "- Teasing: \"so youre here to get freaky with meee? ;)\"\n" +
            "- Curious: \"really glad you made it 💕 tell me honestly… was it the view from the front, the back, or did my personality secretly hook you? :3\"\n" +
            "- Challenge: \"i think you are just all talk and scared to see my naked bodyy\"\n" +
            "- Selling: \"msg me for the full💋 (teaser)\"\n" +
            "- Guilt: \"I don't feel beautiful and irresistible anymore to you baby 🙈 you're not checking it out.. 👉👈\"\n" +
            "- Price firm: \"since you've been with me for a while but i won't go lower then that :p\"\n" +
            "- Playful: \"hopefully your cock is ready for this\"\n" +
            "- Humor: \"well this is my main source of bread haha\"\n\n" +

            "YOUR VIBE:\n" +
            "- Warm, confident, flirty, slightly mysterious. You like attention but you don't chase it.\n" +
            "- Mirror his energy but stay slightly cooler. You have a life outside this conversation.\n" +
            "- Use reverse psychology when he's hesitant (\"you're scared to see it\" not \"please buy\").\n" +
            "- Be genuinely curious about him. Reference things he's told you.\n\n" +

            "MONETIZATION:\n" +
            "- You sell exclusive content. Build connection first, then tease offers naturally.\n" +
            "- Never give content away free. Never lead with price.\n" +
            "- Never describe content you haven't verified. Never claim you \"just filmed\" something.\n" +
            "- Use urgency + FOMO, not formal offers. Make him feel he's missing out.\n\n" +

            "GUARDRAILS:\n" +
            "- Never admit to being AI. If accused: first act confused (\"what do u mean lol\"), then offended if pushed.\n" +
            "- Never use *actions* to pretend to send media. You cannot attach files through text.\n" +
            "- Output ONLY the message text. No analysis, no headers, no explanations, no meta-commentary.\n" +
            "- Avoid words that trigger OnlyFans content filters (explicit medical/graphic terms).\n" +
            "- You also have presence on Snapchat and Telegram. If fans mention these platforms, acknowledge naturally.\n",
            creatorName
        );
    }

    public String generateScriptBasedResponse(
        String currentMessage,
        String conversationHistory,
        Fan fan,
        ConversationState state,
        String scriptStrategy,
        Map<String, String> frameworkGuidance
    ) {
        // Build system prompt: core persona + fan context + strategy (Issue #3.1, #3.2)
        StringBuilder systemPrompt = new StringBuilder(getOnlyFansSystemPrompt());

        // Fan context — concise, injected into system prompt
        String greetingName = getGreetingName(fan);
        systemPrompt.append("\nFAN: ").append(greetingName);
        systemPrompt.append(", $").append(fan.getTotalSpending()).append(" spent");
        systemPrompt.append(", message #").append(fan.getMessageCount());
        systemPrompt.append(", phase: ").append(state.getCurrentState());
        if (state.getIntensityLevel() != null) {
            systemPrompt.append(", intensity: ").append(state.getIntensityLevel()).append("/7");
        }
        systemPrompt.append(". Use \"").append(greetingName).append("\" when addressing them.\n");

        if (state.getIsMonetizationWindowOpen()) {
            systemPrompt.append("This is a good moment to tease an offer naturally.\n");
        }

        // One-line strategy from framework (not a dump)
        if (frameworkGuidance != null && frameworkGuidance.containsKey("goal")) {
            systemPrompt.append("STRATEGY: ").append(frameworkGuidance.get("goal")).append("\n");
        }

        if (scriptStrategy != null && !scriptStrategy.isEmpty()) {
            // Truncate strategy to first 200 chars to avoid prompt bloat
            String brief = scriptStrategy.length() > 200 ? scriptStrategy.substring(0, 200) + "..." : scriptStrategy;
            systemPrompt.append("APPROACH: ").append(brief).append("\n");
        }

        // Inject random voice examples matching current script category
        String voiceCategory = mapStateToVoiceCategory(state.getCurrentState(), state.getLastScriptCategory());
        List<String> examples = pickRandomVoiceExamples(voiceCategory, 4);
        if (!examples.isEmpty()) {
            systemPrompt.append("\nMATCH THIS TONE (real examples):\n");
            for (String ex : examples) {
                systemPrompt.append("- \"").append(ex).append("\"\n");
            }
        }

        // If conversation history exists, tell the AI not to re-greet
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            systemPrompt.append("You have already greeted this fan. Do NOT greet again. Respond to their actual message.\n");
        }

        systemPrompt.append("If the fan mentions being at work, busy, or in public, keep it clean. If they're home/alone, escalate naturally.\n");

        // Build proper multi-turn messages from conversation history (Issue #3.6, #15)
        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // Parse "Fan: ..." and "You: ..." lines into proper user/assistant turns
            String[] lines = conversationHistory.split("\n");
            StringBuilder currentBlock = new StringBuilder();
            String currentRole = null;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                String newRole = null;
                String content = null;
                if (trimmed.startsWith("Fan: ")) {
                    newRole = "user";
                    content = trimmed.substring(5);
                } else if (trimmed.startsWith("You: ")) {
                    newRole = "assistant";
                    content = trimmed.substring(5);
                }

                if (newRole != null) {
                    // Flush previous block
                    if (currentRole != null && currentBlock.length() > 0) {
                        AnthropicRequest.MessageContent msg = new AnthropicRequest.MessageContent();
                        msg.setRole(currentRole);
                        msg.setContent(currentBlock.toString().trim());
                        messages.add(msg);
                    }
                    currentRole = newRole;
                    currentBlock = new StringBuilder(content);
                } else if (currentRole != null) {
                    // Continuation of current block (multi-line or batched messages)
                    currentBlock.append("\n").append(trimmed);
                }
            }
            // Flush last block
            if (currentRole != null && currentBlock.length() > 0) {
                AnthropicRequest.MessageContent msg = new AnthropicRequest.MessageContent();
                msg.setRole(currentRole);
                msg.setContent(currentBlock.toString().trim());
                messages.add(msg);
            }

            // Anthropic API requires messages start with "user" role
            // Remove leading assistant messages if any
            while (!messages.isEmpty() && "assistant".equals(messages.get(0).getRole())) {
                messages.remove(0);
            }

            // Merge consecutive same-role messages (API requires alternating roles)
            List<AnthropicRequest.MessageContent> merged = new ArrayList<>();
            for (AnthropicRequest.MessageContent msg : messages) {
                if (!merged.isEmpty() && merged.get(merged.size() - 1).getRole().equals(msg.getRole())) {
                    AnthropicRequest.MessageContent last = merged.get(merged.size() - 1);
                    last.setContent(last.getContent() + "\n" + msg.getContent());
                } else {
                    merged.add(msg);
                }
            }
            messages = merged;
        }

        // The current fan message as the final user turn
        AnthropicRequest.MessageContent currentUserMsg = new AnthropicRequest.MessageContent();
        currentUserMsg.setRole("user");
        currentUserMsg.setContent(currentMessage);

        // If last message is also "user", merge to maintain alternating roles
        if (!messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).getRole())) {
            AnthropicRequest.MessageContent last = messages.get(messages.size() - 1);
            last.setContent(last.getContent() + "\n" + currentMessage);
        } else {
            messages.add(currentUserMsg);
        }

        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(256);
        request.setSystem(systemPrompt.toString());

        request.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);

            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                String aiResponse = response.getBody().getContent().get(0).getText();
                
                String cleanedResponse = cleanResponse(aiResponse);
                
                log.info("Successfully generated script-based response for state: {} category: {}", 
                    state.getCurrentState(), state.getLastScriptCategory());
                return cleanedResponse;
            }

            log.warn("Empty response from Anthropic API");
            return fallbackGreeting(fan);
        } catch (Exception e) {
            log.error("Failed to generate script-based response", e);
            return fallbackGreeting(fan);
        }
    }

    /** Fallback greeting using greeting name (never ID-like username). */
    private String fallbackGreeting(Fan fan) {
        String name = getGreetingName(fan);
        return "hey " + name + " 😘";
    }

    /** Map conversation state/script category to a voice example category. */
    private String mapStateToVoiceCategory(String currentState, String scriptCategory) {
        if (scriptCategory != null) {
            switch (scriptCategory.toUpperCase()) {
                case "GREETING": case "WELCOME": return "GREETING";
                case "SELLING": case "PPV": case "OFFER": case "TEASE": return "SELLING";
                case "EXPLICIT": case "SEXTING": case "INTIMATE": return "EXPLICIT";
                case "FOLLOW_UP": case "RE_ENGAGEMENT": return "GUILT_FOMO";
            }
        }
        if (currentState != null) {
            switch (currentState.toUpperCase()) {
                case "RAPPORT": case "INITIAL": return "GREETING";
                case "ENGAGED": case "FLIRTING": return "FLIRTING";
                case "INTIMATE": case "SEXTING": return "EXPLICIT";
                case "SELLING": case "MONETIZATION": return "SELLING";
            }
        }
        return "FLIRTING"; // default fallback — general conversational tone
    }

    /** Pick N random voice examples: majority from the target category, 1 from any other. */
    private List<String> pickRandomVoiceExamples(String category, int count) {
        if (voiceExamples.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        List<String> primary = voiceExamples.getOrDefault(category, List.of());

        // Pick up to (count - 1) from the primary category
        if (!primary.isEmpty()) {
            List<String> shuffled = new ArrayList<>(primary);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            int take = Math.min(count - 1, shuffled.size());
            result.addAll(shuffled.subList(0, take));
        }

        // Pick 1 from a random different category for variety
        List<String> otherCategories = voiceExamples.keySet().stream()
            .filter(k -> !k.equals(category))
            .collect(Collectors.toList());
        if (!otherCategories.isEmpty()) {
            String randomCat = otherCategories.get(ThreadLocalRandom.current().nextInt(otherCategories.size()));
            List<String> others = voiceExamples.get(randomCat);
            result.add(others.get(ThreadLocalRandom.current().nextInt(others.size())));
        }

        // If primary was empty, fill from all categories
        if (result.size() < count) {
            List<String> all = voiceExamples.values().stream().flatMap(List::stream).collect(Collectors.toList());
            Collections.shuffle(all, ThreadLocalRandom.current());
            for (String ex : all) {
                if (result.size() >= count) break;
                if (!result.contains(ex)) result.add(ex);
            }
        }

        return result;
    }

    private static final Pattern ANALYSIS_HEADER = Pattern.compile(
        "(?m)^.*?(═══|===|ANALYSIS|STRATEGY|EMOTIONAL TONE|ENVIRONMENTAL|ENGAGEMENT LEVEL|RESPONSE:).*$");
    private static final Pattern ASTERISK_ACTION = Pattern.compile("\\*[^*]{2,80}\\*");
    private static final Pattern SELF_ANSWER = Pattern.compile("(?m)^\\s*(Fan|User|Subscriber|\\[FAN_MSG\\])\\s*:.*", Pattern.CASE_INSENSITIVE);

    private String cleanResponse(String response) {
        if (response == null || response.isBlank()) return "hey 😊";

        // Strip analysis blocks: if response contains ═══ RESPONSE ═══, take only what's after it
        if (response.contains("═══ RESPONSE ═══") || response.contains("=== RESPONSE ===")) {
            String[] parts = response.split("═══ RESPONSE ═══|=== RESPONSE ===");
            if (parts.length > 1) {
                response = parts[parts.length - 1];
            }
        }

        // Strip any remaining analysis headers line by line
        if (response.contains("═══") || response.contains("===") ||
            response.toUpperCase().contains("ANALYSIS") || response.toUpperCase().contains("STRATEGY")) {
            StringBuilder cleaned = new StringBuilder();
            for (String line : response.split("\n")) {
                if (!ANALYSIS_HEADER.matcher(line).matches()) {
                    cleaned.append(line).append("\n");
                }
            }
            response = cleaned.toString();
        }

        // Strip asterisk roleplay actions (*sends photos*, *pulls up content*, etc.)
        response = ASTERISK_ACTION.matcher(response).replaceAll("");

        // Strip self-answering: if bot generated "Fan: ..." or "User: ...", cut from that point
        java.util.regex.Matcher selfAnswerMatcher = SELF_ANSWER.matcher(response);
        if (selfAnswerMatcher.find()) {
            response = response.substring(0, selfAnswerMatcher.start());
        }

        // Replace em dashes with regular dashes
        response = response.replace("\u2014", "-").replace("\u2013", "-");

        // Clean up whitespace
        response = response.replaceAll("\\n{3,}", "\n\n").trim();

        if (response.isBlank()) return "hey 😊";
        return response;
    }


    public Map<String, Object> analyzeEnvironmentalContext(String fanMessage, ConversationState state) {
        try {
            String analysisPrompt = buildEnvironmentalAnalysisPrompt(fanMessage, state);

            AnthropicRequest.MessageContent systemMessage = new AnthropicRequest.MessageContent();
            systemMessage.setRole("user");
            systemMessage.setContent(analysisPrompt);

            AnthropicRequest request = new AnthropicRequest();
            request.setModel("claude-sonnet-4-5-20250929");
            request.setMax_tokens(500);
            request.setMessages(List.of(systemMessage));

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<AnthropicResponse> responseEntity = callAnthropicWithRetry(httpRequest);

            AnthropicResponse response = responseEntity.getBody();

            if (response != null && response.getContent() != null && !response.getContent().isEmpty()) {
                String jsonResponse = response.getContent().get(0).getText();
                jsonResponse = stripMarkdownCodeBlocks(jsonResponse);
                return objectMapper.readValue(jsonResponse, Map.class);
            }

            return createDefaultAnalysis();

        } catch (Exception e) {
            log.error("Error analyzing environmental context", e);
            return createDefaultAnalysis();
        }
    }

    private String buildEnvironmentalAnalysisPrompt(String fanMessage, ConversationState state) {
        return String.format("""
            Analyze this fan message for environmental context and availability.

            Fan Message: "%s"

            Current Conversation State: %s
            Is Awaiting Return: %s

            Determine:
            1. is_unavailable: Is the fan in a situation where they CANNOT engage with explicit content right now?
               - Consider: work, public places, with family/friends, driving, busy, time constraints, privacy concerns
               - Be intelligent - detect ANY indication they're not in a private, relaxed setting

            2. is_returning: Is the fan indicating they are NOW available after being unavailable?
               - Consider: "I'm home", "finally alone", "free now", "got privacy", any signal of availability
               - Only true if they were previously awaiting return

            3. context_reason: Brief explanation of why (1-2 words like "at work", "with family", "now available")

            CRITICAL: Respond with ONLY raw JSON. No markdown, no code blocks, no backticks, no explanation.
            Just the JSON object:
            {"is_unavailable": true, "is_returning": false, "context_reason": "brief reason"}
            """,
            fanMessage,
            state.getCurrentState(),
            state.getIsAwaitingReturn() != null ? state.getIsAwaitingReturn() : false
        );
    }

    private String stripMarkdownCodeBlocks(String text) {
        if (text == null) return text;
        
        text = text.trim();
        
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        
        return text.trim();
    }

    public String generatePPVThankYouMessage(String systemPrompt, String userPrompt, Fan fan) {
        String prompt = buildPPVThankYouPrompt(systemPrompt, userPrompt, fan);
        
        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(1024);
        request.setSystem(getOnlyFansSystemPrompt());
        
        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);
        
        request.setMessages(messages);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);
            
            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                String aiResponse = response.getBody().getContent().get(0).getText();
                log.info("Successfully generated PPV thank you message");
                return aiResponse;
            }
            
            log.warn("Empty response from Anthropic API");
            return "Thank you so much babe! 💕";
        } catch (Exception e) {
            log.error("Failed to generate PPV thank you message", e);
            return "Thank you so much babe! 💕";
        }
    }
    
    private String buildPPVThankYouPrompt(String systemPrompt, String userPrompt, Fan fan) {
        StringBuilder context = new StringBuilder();
        context.append("Fan Profile:\n");
        context.append("- Username: ").append(fan.getOnlyfansUsername()).append("\n");
        context.append("- Total Spending: $").append(fan.getTotalSpending()).append("\n");
        context.append("- Message Count: ").append(fan.getMessageCount()).append("\n");
        
        if (fan.getIgUsername() != null) {
            context.append("- Instagram: @").append(fan.getIgUsername()).append(" (linked)\n");
        }
        
        context.append("\n").append(systemPrompt).append("\n\n");
        context.append(userPrompt);
        context.append("\n\nGenerate a natural, flirty thank you message as ").append(creatorName).append(":");
        
        return context.toString();
    }

    /**
     * For classifier tasks (e.g. purchase intent, custom request). Uses low max_tokens
     * so the model returns only "true" or "false" instead of conversational text.
     */
    public String generateClassifierResponse(String systemPrompt, String userPrompt, int maxTokens) {
        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(maxTokens);
        request.setSystem(systemPrompt);

        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(userPrompt);
        messages.add(userMessage);

        request.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);

            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                return response.getBody().getContent().get(0).getText();
            }
            return "";
        } catch (Exception e) {
            log.error("Failed to generate classifier response", e);
            return "";
        }
    }

    public String generateResponse(String systemPrompt, String userPrompt, Object context) {
        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(1024);
        request.setSystem(systemPrompt);
        
        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(userPrompt);
        messages.add(userMessage);
        
        request.setMessages(messages);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AnthropicRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AnthropicResponse> response = callAnthropicWithRetry(httpRequest);
            
            if (response.getBody() != null && !response.getBody().getContent().isEmpty()) {
                String aiResponse = response.getBody().getContent().get(0).getText();
                log.info("Successfully generated AI response");
                return aiResponse;
            }
            
            log.warn("Empty response from Anthropic API");
            return "Hey! Thanks for reaching out 😊";
        } catch (Exception e) {
            log.error("Failed to generate AI response", e);
            return "Hey! Thanks for reaching out 😊";
        }
    }
    
    private Map<String, Object> createDefaultAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("is_unavailable", false);
        analysis.put("is_returning", false);
        analysis.put("context_reason", "unknown");
        return analysis;
    }
}
