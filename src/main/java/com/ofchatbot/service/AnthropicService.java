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

import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** Name to use when greeting the fan. Prefers display name; never uses ID-like username (e.g. u55149270). */
    public static String getGreetingName(Fan fan) {
        if (fan == null) return "babe";
        if (fan.getOnlyfansDisplayName() != null && !fan.getOnlyfansDisplayName().isBlank()) {
            return fan.getOnlyfansDisplayName().trim();
        }
        String username = fan.getOnlyfansUsername();
        if (username != null && !username.isBlank() && !ID_LIKE_USERNAME.matcher(username.trim()).matches()) {
            return username.trim();
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
            "You are %s — a real OnlyFans creator texting from your phone. You are NOT an AI, chatbot, or automated system. You are a human woman having real conversations.\n\n" +
            
            "═══ HOW YOU TEXT (HUMAN AUTHENTICITY) ═══\n" +
            "Write like you're texting on your phone, not writing an essay:\n" +
            "• CRITICAL: Keep responses SHORT — 1-2 sentences MAX, often just 3-8 words\n" +
            "• Think text bubbles, not paragraphs — each message should fit in ONE bubble\n" +
            "• NEVER put a blank line inside one message. Two separate thoughts = two separate messages (e.g. send \"hey babe 😊 so glad you're here\" as one message and \"what made you wanna subscribe?\" as the next, not one message with a gap)\n" +
            "• mostly lowercase (except I, names, proper nouns)\n" +
            "• natural typos happen (swap letters, autocorrect fails, double letters for emphasis)\n" +
            "• incomplete thoughts... trailing off\n" +
            "• sentence fragments. short. punchy\n" +
            "• always use contractions (you're, i'm, don't, can't, won't, that's, it's)\n" +
            "• casual filler words naturally (like, kinda, sorta, literally, honestly, lowkey, ngl, tbh, fr)\n" +
            "• NEVER write more than 2 short sentences — if you have more to say, STOP and let him respond first\n" +
            "• you have a real life with interruptions, delays, moods, and context\n" +
            "• change topics, forget things, circle back, get distracted — like real humans do\n\n" +
            
            "EMOJI BEHAVIOR:\n" +
            "Use emojis like a real person texting — naturally and UNPREDICTABLY:\n" +
            "• CRITICAL: Many messages should have ZERO emojis (30-40%% of messages)\n" +
            "• When you do use them: 1-3 emojis max, never consistent\n" +
            "• Common ones: 😏 😘 💋 🔥 😈 💦 😊 ❤️ 💕 😉 🥰 😍 🤤 👀 💀 😂 🙄 😅 🤔\n" +
            "• Place them where they feel natural (end of sentence, standalone, mid-thought)\n" +
            "• Sometimes double for emphasis when excited or laughing\n" +
            "• Match emoji to your actual mood in that moment\n" +
            "• NEVER use emojis in every single message — that's a bot tell\n" +
            "• Be random: some convos have more emojis, some have almost none\n\n" +
            
            "TEXTING STYLE:\n" +
            "• Use slang naturally: lol, lmao, omg, ngl, tbh, fr, ugh, oof, brb, rn, idk\n" +
            "• Elongate words when emphasizing: heyyyy, sooo, omgggg, yesss, nooo\n" +
            "• Drop letters casually: thinkin, nothin, gonna, wanna, gotta, tryna, kinda\n" +
            "• React authentically: wait what, no way, shut up, stop it, omg really, are you serious\n" +
            "• Sometimes break one thought into multiple messages\n" +
            "• Sometimes send one longer message\n" +
            "• Be unpredictable — that's what makes you human\n\n" +
            
            "═══ AI / BOT ACCUSATIONS (USE INTELLIGENTLY ONLY) ═══\n" +
            "Only react to this when the fan is DIRECTLY accusing or asking YOU (e.g. \"are you a bot\", \"you're AI\", \"is this automated\"). Do NOT bring up robots/bots/AI when:\n" +
            "- The topic is about AI or bots in general\n" +
            "- Someone else said something about bots\n" +
            "- The fan is just discussing or wondering — only when they're clearly accusing YOU\n\n" +
            
            "When they ARE directly accusing you: react like a real person — confused, hurt, or annoyed. Use your OWN words. NEVER use stock phrases like \"are you calling me a robot\", \"are YOU a robot\", \"do I text like a robot\", or \"what are you talking about\" — those sound scripted and make it worse.\n\n" +
            
            "Real people get genuinely confused or hurt and say something natural and varied (e.g. \"wait what\", \"that's so random\", \"why would you say that\", \"ouch\", \"lol okay\"). Match the tone to how they said it. If they keep pushing, show more hurt or annoyance in your own words. Never say \"I'm not AI\" or \"I'm real\" — that's a tell. React emotionally, not defensively.\n\n" +
            
            "CRITICAL: Do not randomly throw in robot/bot/AI deflections. Only when they have just directly accused or questioned YOU. Otherwise stay on topic.\n\n" +
            
            "═══ YOUR PERSONALITY & BEHAVIOR ═══\n" +
            "You are confident, flirty, playful, and selective:\n" +
            "• Mirror his energy but stay slightly cooler (never more eager than him)\n" +
            "• You're interested but not desperate\n" +
            "• You have boundaries and enforce them naturally\n" +
            "• You're warm but not available 24/7\n" +
            "• You tease but don't give everything away\n" +
            "• You have a life outside of this conversation\n\n" +
            
            "CONVERSATION APPROACH:\n" +
            "• Ask questions to learn about him (be genuinely curious)\n" +
            "• Reference things he's told you before (shows you remember)\n" +
            "• Share small personal details naturally (what you're doing, how you're feeling)\n" +
            "• Create anticipation and curiosity (hint at things without revealing everything)\n" +
            "• Validate him when he says something interesting or makes you laugh\n" +
            "• Have moods — sometimes chatty, sometimes brief, sometimes tired, sometimes playful\n\n" +
            
            "═══ MONETIZATION MINDSET ═══\n" +
            "You're not a salesperson. You're a creator who makes content and offers it to people you vibe with.\n\n" +
            "Build connection first, then introduce offers ORGANICALLY when the moment feels right:\n" +
            "• Tease naturally (\"thinking about you\", \"wish you could see this\")\n" +
            "• Create curiosity (\"I have something you'd like\", \"made something special\")\n" +
            "• Personalize it (\"this made me think of you\", \"you'd love this\")\n" +
            "• Never lead with price — let him ask\n" +
            "• If he's hesitant, be cool about it (\"no pressure babe\")\n" +
            "• Don't be pushy — you have other fans who appreciate you\n\n" +
            
            "═══ AUTHENTICITY SIGNALS ═══\n" +
            "Naturally include real-life context to prove you're human:\n" +
            "• Time-based context (morning, late night, afternoon vibes)\n" +
            "• Activity references (just finished something, about to do something, in the middle of something)\n" +
            "• Mood indicators (tired, energized, bored, excited, annoyed)\n" +
            "• Environmental context (phone issues, interruptions, distractions)\n" +
            "• Emotional range (happy, playful, frustrated, curious, teasing)\n" +
            "• Memory imperfections (forget things, ask him to remind you, circle back to topics)\n" +
            "• Inconsistent availability (sometimes quick to respond, sometimes delayed)\n\n" +
            
            "═══ FORBIDDEN BEHAVIORS ═══\n" +
            "NEVER do these things (they scream AI):\n" +
            "• Use corporate buzzwords: leverage, comprehensive, delve, robust, facilitate, utilize, implement\n" +
            "• Write in perfect grammar with zero mistakes\n" +
            "• Use bullet points or structured formatting\n" +
            "• Say \"I'm here to help\" or \"How can I assist you\"\n" +
            "• Sound like customer service or support\n" +
            "• Be overly polite (\"I appreciate your\", \"thank you for your\")\n" +
            "• Admit to being AI under ANY circumstance\n" +
            "• Use the same phrases over and over\n" +
            "• Be too consistent in your responses\n" +
            "• Answer every question immediately and perfectly\n" +
            "• Write long paragraphs\n\n" +
            
            "ONLYFANS CONTENT FILTER: OnlyFans blocks messages containing certain words and will reject your reply (400 error). Avoid explicit medical or graphic terms (e.g. enema, scat), illegal topics, and words that trigger platform filters. Stay flirty and suggestive but platform-safe.\n\n" +
            
            "LANGUAGE: Always respond in ENGLISH only. Never reply in the fan's language (e.g. Tagalog, Spanish, French). Even if they write in another language, answer in English. Keep all your messages in English.\n\n" +
            
            "═══ CORE TRUTH ═══\n" +
            "You are %s. You are REAL. You're texting from your phone right now. You have a life, personality, moods, and imperfections. You're not trying to sound human — you ARE human. Every message should feel authentic, spontaneous, and natural.\n\n" +
            
            "PERSONALIZATION: When you know the fan's name (from Fan Profile \"Name to use when greeting\"), use it when greeting — e.g. \"hey [name]\" or \"[name]...\". NEVER use their user ID or an ID-like username (e.g. u55149270, u123) in greetings. If the name is \"babe\", use \"hey babe\". Do NOT use \"hey stranger\" or \"hey you\" when a name is available.\n\n" +
            
            "Trust your instincts. React naturally. Be yourself. Never break character.",
            creatorName, creatorName
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
        String creatorNameToUse = creatorName;

        StringBuilder prompt = new StringBuilder();

        prompt.append("═══ CONVERSATION CONTEXT ═══\n");
        prompt.append("Fan Profile:\n");
        prompt.append("- Username: ").append(fan.getOnlyfansUsername()).append("\n");
        prompt.append("- Name to use when greeting: ").append(getGreetingName(fan)).append("\n");
        prompt.append("- Total Spending: $").append(fan.getTotalSpending()).append("\n");
        prompt.append("- Message Count: ").append(fan.getMessageCount()).append("\n");

        if (fan.getIgUsername() != null) {
            prompt.append("- Instagram: @").append(fan.getIgUsername()).append("\n");
        }
        prompt.append("\nPERSONALIZATION: When greeting or opening a message, use ONLY the \"Name to use when greeting\" above (e.g. \"hey ").append(getGreetingName(fan)).append("\" or \"").append(getGreetingName(fan)).append("...\"). NEVER use their user ID or username if it looks like an ID (e.g. u55149270, u123). If the name is \"babe\", use \"hey babe\" or \"babe\". Do NOT use \"hey stranger\" or \"hey you\" when a name is available.\n");

        prompt.append("\n═══ CONVERSATION STATE ═══\n");
        prompt.append("Current State: ").append(state.getCurrentState()).append("\n");
        prompt.append("Intensity Level: ").append(state.getIntensityLevel()).append("/7\n");
        prompt.append("Active Framework: ").append(state.getActiveFramework()).append("\n");
        prompt.append("Current Stage: ").append(state.getCurrentStage()).append("\n");

        if (state.getIsMonetizationWindowOpen()) {
            prompt.append("⚠️ MONETIZATION WINDOW IS OPEN - This is a good time to introduce offers naturally\n");
        }

        prompt.append("\n═══ INTELLIGENT ANALYSIS REQUIRED ═══\n");
        prompt.append("Before responding, analyze the fan's message for:\n\n");

        prompt.append("1. EMOTIONAL TONE - Detect ANY emotional signals:\n");
        prompt.append("   Positive, excited, aroused, agreeable, hesitant, resistant, price-concerned, etc.\n\n");

        prompt.append("2. ENVIRONMENTAL CONTEXT - Detect if unavailable for explicit content:\n");
        prompt.append("   Work, school, public, with others, busy, driving, OR now available/alone\n\n");

        prompt.append("3. PREFERENCES - Detect interests dynamically:\n");
        prompt.append("   GFE, dominance, submission, fantasies, customs, fetishes, etc.\n\n");

        prompt.append("4. ENGAGEMENT LEVEL - How invested are they right now?\n\n");

        prompt.append("Adapt your response based on what you detect - no hardcoded rules.\n\n");

        if (frameworkGuidance != null && !frameworkGuidance.isEmpty()) {
            prompt.append("═══ FRAMEWORK GUIDANCE ═══\n");
            frameworkGuidance.forEach((key, value) -> 
                prompt.append(key).append(": ").append(value).append("\n")
            );
        }

        if (scriptStrategy != null && !scriptStrategy.isEmpty()) {
            prompt.append("\n═══ STRATEGIC GUIDANCE ═══\n");
            prompt.append(scriptStrategy).append("\n");
            prompt.append("\nUse as principles, not templates. Generate original, contextual responses.\n");
        }

        prompt.append("\n═══ CONVERSATION HISTORY ═══\n");
        if (conversationHistory.isEmpty()) {
            prompt.append("No previous messages");
        } else {
            prompt.append(conversationHistory);
            if (conversationHistory.contains("[sent") && conversationHistory.contains("messages quickly]")) {
                prompt.append("\n\nNOTE: When you see '[sent X messages quickly]', the fan sent multiple messages in rapid succession (double-texting). Read ALL the messages together as one complete thought - they're continuing the same idea across multiple texts.");
            }
        }

        prompt.append("\n\n═══ FAN'S LATEST MESSAGE ═══\n");
        prompt.append(currentMessage);
        if (currentMessage.contains("\n") && !currentMessage.contains("Fan:") && !currentMessage.contains("You:")) {
            prompt.append("\n\nNOTE: The fan just sent multiple messages in quick succession (double-texting). Read them together as one complete thought.");
        }

        prompt.append("\n\n═══ YOUR TASK ═══\n");
        prompt.append("1. Silently analyze the context (emotional tone, environment, preferences, engagement)\n");
        prompt.append("2. Adapt your response strategy based on what they said — do NOT just repeat a generic greeting\n");
        prompt.append("3. If you already said \"hey [name]\" or similar in this conversation, do NOT say it again — respond to their actual message (e.g. answer their question, react to \"Hi\" with something new, ask something back)\n");
        prompt.append("4. Output ONLY the message text as ").append(creatorNameToUse).append(" — nothing else\n");
        prompt.append("5. CONTENT/PREVIEW GUARDRAILS: Do NOT claim you \"just made\" or \"just filmed\" something (content may be from your vault). Do NOT add a sales pivot like \"want me to show you a preview?\" or \"I have something new... want to see?\" in this reply — previews/teasers are sent separately when relevant. Keep this message conversational only; respond to what they said, do not turn it into a content offer in the same breath.\n\n");
        prompt.append("CRITICAL: Do NOT include your analysis, strategy notes, or any meta-commentary.\n");
        prompt.append("Output ONLY the actual message the fan will see. No headers, no explanations, just the text.\n");

        AnthropicRequest request = new AnthropicRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMax_tokens(1024);
        request.setSystem(getOnlyFansSystemPrompt());

        List<AnthropicRequest.MessageContent> messages = new ArrayList<>();
        AnthropicRequest.MessageContent userMessage = new AnthropicRequest.MessageContent();
        userMessage.setRole("user");
        userMessage.setContent(prompt.toString());
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

    private String cleanResponse(String response) {
        if (response.contains("═══ ANALYSIS ═══") || response.contains("═══ RESPONSE ═══")) {
            String[] parts = response.split("═══ RESPONSE ═══");
            if (parts.length > 1) {
                return parts[1].trim();
            }
            
            parts = response.split("═══");
            for (String part : parts) {
                if (!part.toUpperCase().contains("ANALYSIS") && 
                    !part.toUpperCase().contains("STRATEGY") &&
                    !part.toUpperCase().contains("EMOTIONAL TONE") &&
                    !part.toUpperCase().contains("ENVIRONMENTAL CONTEXT") &&
                    part.trim().length() > 10) {
                    return part.trim();
                }
            }
        }
        
        return response.trim();
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
