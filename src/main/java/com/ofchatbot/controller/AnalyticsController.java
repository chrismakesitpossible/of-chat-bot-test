package com.ofchatbot.controller;

import com.ofchatbot.service.ScriptAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {
    
    private final ScriptAnalyticsService scriptAnalyticsService;
    
    @GetMapping("/dashboard/{creatorId}")
    public ResponseEntity<Map<String, Object>> getDashboard(@PathVariable String creatorId) {
        log.info("Fetching analytics dashboard for creator: {}", creatorId);
        Map<String, Object> dashboard = scriptAnalyticsService.getPerformanceDashboard(creatorId);
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/category/{creatorId}/{scriptCategory}")
    public ResponseEntity<Map<String, Object>> getCategoryAnalysis(
            @PathVariable String creatorId,
            @PathVariable String scriptCategory) {
        log.info("Fetching category analysis for creator: {} category: {}", creatorId, scriptCategory);
        Map<String, Object> analysis = scriptAnalyticsService.getScriptCategoryAnalysis(creatorId, scriptCategory);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/recent/{creatorId}")
    public ResponseEntity<List<Map<String, Object>>> getRecentPerformance(
            @PathVariable String creatorId,
            @RequestParam(defaultValue = "7") int days) {
        log.info("Fetching recent performance for creator: {} (last {} days)", creatorId, days);
        List<Map<String, Object>> performance = scriptAnalyticsService.getRecentPerformance(creatorId, days);
        return ResponseEntity.ok(performance);
    }
    
    @GetMapping("/recommendations/{creatorId}")
    public ResponseEntity<Map<String, Object>> getRecommendations(@PathVariable String creatorId) {
        log.info("Fetching optimization recommendations for creator: {}", creatorId);
        Map<String, Object> recommendations = scriptAnalyticsService.getOptimizationRecommendations(creatorId);
        return ResponseEntity.ok(recommendations);
    }
}
