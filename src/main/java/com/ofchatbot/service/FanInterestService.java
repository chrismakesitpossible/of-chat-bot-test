package com.ofchatbot.service;

import com.ofchatbot.entity.FanInterest;
import com.ofchatbot.entity.Message;
import com.ofchatbot.repository.FanInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FanInterestService {

    private final FanInterestRepository fanInterestRepository;
    private final AnthropicService anthropicService;

    public void extractAndTrackInterests(Long fanId, List<Message> messages) {
        // Use AI to extract interests from conversation history
        String analysisPrompt = String.format(
            "Extract fan interests from this conversation history.\n\n" +
            "Messages: %s\n\n" +
            "Extract ONLY interest categories from this fixed list (lowercase, exactly as written):\n" +
            "lingerie, feet, roleplay, outdoor, shower, workout, cosplay, toys, anal, oral, solo, couple, domination, submission, tease,\n" +
            "oily, wet, nude, stockings, barefoot, socks, robe,\n" +
            "hotel, car, kitchen, bedroom, couch, vacation,\n" +
            "joi, bj, doggy, missionary, cowgirl, squirt, creampie, facial, swallow, fingering, dildo, vibrator,\n" +
            "dominant, submissive, rough, romantic, slow, intense, shy, gfe, humiliation,\n" +
            "short, medium, long,\n" +
            "bbc, custom, teaser, rapport\n\n" +
            "Return ONLY a comma-separated list of these categories (no new words) or 'none' if no interests found.",
            messages.stream()
                .map(Message::getContent)
                .collect(Collectors.joining("\n"))
        );
        
        try {
            String aiResult = anthropicService.generateResponse(
                "You are an interest extractor. Extract interests and return only comma-separated categories or 'none'.",
                analysisPrompt,
                null
            );
            
            String cleanResult = aiResult.toLowerCase().trim();
            if (cleanResult.equals("none")) {
                log.info("No interests detected for fan {}", fanId);
                return;
            }
            
            // Parse and save interests
            String[] interests = cleanResult.split(",");
            for (String interest : interests) {
                String trimmed = interest.trim();
                if (!trimmed.isEmpty()) {
                    saveInterestCategory(fanId, trimmed);
                }
            }
            
            log.info("Extracted interests for fan {}: {}", fanId, interests);
            
        } catch (Exception e) {
            log.error("Failed to extract interests with AI", e);
        }
    }
    
    private void saveInterestCategory(Long fanId, String category) {
        Optional<FanInterest> existingOpt = fanInterestRepository.findByFanIdAndCategory(fanId, category);
        
        if (existingOpt.isPresent()) {
            FanInterest existing = existingOpt.get();
            existing.setMentionCount(existing.getMentionCount() + 1);
            fanInterestRepository.save(existing);
        } else {
            FanInterest newInterest = new FanInterest();
            newInterest.setFanId(fanId);
            newInterest.setCategory(category);
            newInterest.setMentionCount(1);
            fanInterestRepository.save(newInterest);
        }
    }
    
    public List<String> getTopInterestCategories(Long fanId, int limit) {
        List<FanInterest> interests = fanInterestRepository.findByFanIdOrderByMentionCountDesc(fanId);
        
        return interests.stream()
            .limit(limit)
            .map(FanInterest::getCategory)
            .collect(Collectors.toList());
    }
}
