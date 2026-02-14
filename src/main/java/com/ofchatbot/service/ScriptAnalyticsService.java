package com.ofchatbot.service;

import com.ofchatbot.entity.ScriptPerformance;
import com.ofchatbot.repository.ScriptPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptAnalyticsService {
    
    private final ScriptPerformanceRepository scriptPerformanceRepository;
    
    @Transactional
    public void trackScriptUsage(String creatorId, String scriptCategory, String conversationState) {
        ScriptPerformance performance = getOrCreatePerformance(creatorId, scriptCategory, conversationState);
        performance.incrementUsage();
        scriptPerformanceRepository.save(performance);
        
        log.debug("Tracked script usage: {} in state: {}", scriptCategory, conversationState);
    }
    
    @Transactional
    public void trackResponse(String creatorId, String scriptCategory, String conversationState, int engagementScore) {
        ScriptPerformance performance = getOrCreatePerformance(creatorId, scriptCategory, conversationState);
        performance.recordResponse(engagementScore);
        scriptPerformanceRepository.save(performance);
        
        log.debug("Tracked response for {}: engagement score {}", scriptCategory, engagementScore);
    }
    
    @Transactional
    public void trackPurchase(String creatorId, String scriptCategory, String conversationState, double amount) {
        ScriptPerformance performance = getOrCreatePerformance(creatorId, scriptCategory, conversationState);
        performance.recordPurchase(amount);
        scriptPerformanceRepository.save(performance);
        
        log.info("Tracked purchase for {}: ${}", scriptCategory, amount);
    }
    
    private ScriptPerformance getOrCreatePerformance(String creatorId, String scriptCategory, String conversationState) {
        return scriptPerformanceRepository
            .findByCreatorIdAndScriptCategoryAndConversationState(creatorId, scriptCategory, conversationState)
            .orElseGet(() -> {
                ScriptPerformance newPerformance = new ScriptPerformance();
                newPerformance.setCreatorId(creatorId);
                newPerformance.setScriptCategory(scriptCategory);
                newPerformance.setConversationState(conversationState);
                return newPerformance;
            });
    }
    
    public Map<String, Object> getPerformanceDashboard(String creatorId) {
        Map<String, Object> dashboard = new HashMap<>();
        
        List<ScriptPerformance> allScripts = scriptPerformanceRepository
            .findByCreatorIdOrderByConversionRateDesc(creatorId);
        
        dashboard.put("total_scripts_tracked", allScripts.size());
        dashboard.put("total_revenue", scriptPerformanceRepository.getTotalRevenueByCreator(creatorId));
        dashboard.put("average_conversion_rate", scriptPerformanceRepository.getAverageConversionRateByCreator(creatorId));
        
        List<Map<String, Object>> topPerformers = allScripts.stream()
            .limit(5)
            .map(this::convertToMap)
            .collect(Collectors.toList());
        dashboard.put("top_performing_scripts", topPerformers);
        
        List<ScriptPerformance> underperforming = scriptPerformanceRepository
            .findUnderperformingScripts(creatorId, 5.0, 10);
        List<Map<String, Object>> underperformingMaps = underperforming.stream()
            .map(this::convertToMap)
            .collect(Collectors.toList());
        dashboard.put("underperforming_scripts", underperformingMaps);
        
        Map<String, Double> categoryRevenue = allScripts.stream()
            .collect(Collectors.groupingBy(
                ScriptPerformance::getScriptCategory,
                Collectors.summingDouble(ScriptPerformance::getTotalRevenueGenerated)
            ));
        dashboard.put("revenue_by_category", categoryRevenue);
        
        Map<String, Double> categoryConversion = allScripts.stream()
            .collect(Collectors.groupingBy(
                ScriptPerformance::getScriptCategory,
                Collectors.averagingDouble(ScriptPerformance::getConversionRate)
            ));
        dashboard.put("conversion_by_category", categoryConversion);
        
        return dashboard;
    }
    
    public Map<String, Object> getScriptCategoryAnalysis(String creatorId, String scriptCategory) {
        List<ScriptPerformance> categoryScripts = scriptPerformanceRepository
            .findByCreatorIdOrderByConversionRateDesc(creatorId)
            .stream()
            .filter(sp -> sp.getScriptCategory().equals(scriptCategory))
            .collect(Collectors.toList());
        
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("script_category", scriptCategory);
        analysis.put("total_uses", categoryScripts.stream().mapToInt(ScriptPerformance::getTimesUsed).sum());
        analysis.put("total_revenue", categoryScripts.stream().mapToDouble(ScriptPerformance::getTotalRevenueGenerated).sum());
        analysis.put("total_purchases", categoryScripts.stream().mapToInt(ScriptPerformance::getPurchasesGenerated).sum());
        analysis.put("average_conversion_rate", categoryScripts.stream()
            .mapToDouble(ScriptPerformance::getConversionRate)
            .average()
            .orElse(0.0));
        analysis.put("average_engagement", categoryScripts.stream()
            .mapToDouble(ScriptPerformance::getAverageEngagementScore)
            .average()
            .orElse(0.0));
        
        Map<String, Map<String, Object>> byState = categoryScripts.stream()
            .collect(Collectors.toMap(
                ScriptPerformance::getConversationState,
                this::convertToMap
            ));
        analysis.put("performance_by_state", byState);
        
        return analysis;
    }
    
    public List<Map<String, Object>> getRecentPerformance(String creatorId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<ScriptPerformance> recentScripts = scriptPerformanceRepository
            .findTopPerformingScripts(creatorId, since);
        
        return recentScripts.stream()
            .map(this::convertToMap)
            .collect(Collectors.toList());
    }
    
    public Map<String, Object> getOptimizationRecommendations(String creatorId) {
        Map<String, Object> recommendations = new HashMap<>();
        List<String> suggestions = new ArrayList<>();
        
        List<ScriptPerformance> allScripts = scriptPerformanceRepository
            .findByCreatorIdOrderByConversionRateDesc(creatorId);
        
        List<ScriptPerformance> topScripts = allScripts.stream()
            .filter(sp -> sp.getConversionRate() > 15.0)
            .limit(3)
            .collect(Collectors.toList());
        
        if (!topScripts.isEmpty()) {
            suggestions.add("Top performing scripts: " + 
                topScripts.stream()
                    .map(sp -> sp.getScriptCategory() + " (" + String.format("%.1f%%", sp.getConversionRate()) + ")")
                    .collect(Collectors.joining(", ")));
        }
        
        List<ScriptPerformance> underperforming = scriptPerformanceRepository
            .findUnderperformingScripts(creatorId, 5.0, 10);
        
        if (!underperforming.isEmpty()) {
            suggestions.add("Consider revising these low-performing scripts: " +
                underperforming.stream()
                    .map(sp -> sp.getScriptCategory() + " (" + String.format("%.1f%%", sp.getConversionRate()) + ")")
                    .collect(Collectors.joining(", ")));
        }
        
        Map<String, Double> statePerformance = allScripts.stream()
            .collect(Collectors.groupingBy(
                ScriptPerformance::getConversationState,
                Collectors.averagingDouble(ScriptPerformance::getConversionRate)
            ));
        
        Optional<Map.Entry<String, Double>> bestState = statePerformance.entrySet().stream()
            .max(Map.Entry.comparingByValue());
        
        if (bestState.isPresent()) {
            suggestions.add("Best performing conversation state: " + 
                bestState.get().getKey() + 
                " with " + String.format("%.1f%%", bestState.get().getValue()) + " conversion");
        }
        
        long highEngagementScripts = allScripts.stream()
            .filter(sp -> sp.getAverageEngagementScore() >= 7.0)
            .count();
        
        if (highEngagementScripts > 0) {
            suggestions.add(highEngagementScripts + " scripts maintain high engagement (7+/10)");
        }
        
        recommendations.put("suggestions", suggestions);
        recommendations.put("top_performers", topScripts.stream().map(this::convertToMap).collect(Collectors.toList()));
        recommendations.put("needs_improvement", underperforming.stream().map(this::convertToMap).collect(Collectors.toList()));
        
        return recommendations;
    }
    
    private Map<String, Object> convertToMap(ScriptPerformance sp) {
        Map<String, Object> map = new HashMap<>();
        map.put("script_category", sp.getScriptCategory());
        map.put("conversation_state", sp.getConversationState());
        map.put("times_used", sp.getTimesUsed());
        map.put("responses_received", sp.getResponsesReceived());
        map.put("purchases_generated", sp.getPurchasesGenerated());
        map.put("total_revenue", String.format("$%.2f", sp.getTotalRevenueGenerated()));
        map.put("conversion_rate", String.format("%.2f%%", sp.getConversionRate()));
        map.put("average_engagement", String.format("%.1f/10", sp.getAverageEngagementScore()));
        map.put("average_revenue_per_use", String.format("$%.2f", sp.getAverageRevenuePerUse()));
        map.put("positive_responses", sp.getPositiveResponses());
        map.put("neutral_responses", sp.getNeutralResponses());
        map.put("negative_responses", sp.getNegativeResponses());
        map.put("last_used", sp.getLastUsedAt());
        return map;
    }
}
