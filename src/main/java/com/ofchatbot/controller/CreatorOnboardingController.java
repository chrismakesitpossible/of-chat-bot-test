package com.ofchatbot.controller;

import com.ofchatbot.dto.CreatorOnboardingRequest;
import com.ofchatbot.dto.CreatorOnboardingResponse;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.service.CreatorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
@Slf4j
public class CreatorOnboardingController {

    private final CreatorService creatorService;
    
    @Value("${server.base-url:http://localhost:8081}")
    private String serverBaseUrl;

    @PostMapping("/onboard")
    public ResponseEntity<CreatorOnboardingResponse> onboardCreator(@RequestBody CreatorOnboardingRequest request) {
        try {
            log.info("Onboarding new creator: {}", request.getName());

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    new CreatorOnboardingResponse(false, "Creator name is required", null, null)
                );
            }

            if (request.getOnlyfansApiKey() == null || request.getOnlyfansApiKey().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    new CreatorOnboardingResponse(false, "OnlyFans API key is required", null, null)
                );
            }

            if (request.getOnlyfansAccountId() == null || request.getOnlyfansAccountId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    new CreatorOnboardingResponse(false, "OnlyFans account ID is required", null, null)
                );
            }

            Optional<Creator> existing = creatorService.findByOnlyfansAccountId(request.getOnlyfansAccountId());
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(
                    new CreatorOnboardingResponse(
                        false, 
                        "Creator with this account ID already exists", 
                        existing.get().getCreatorId(), 
                        null
                    )
                );
            }

            Creator creator = new Creator();
            creator.setCreatorId(request.getOnlyfansAccountId());
            creator.setName(request.getName());
            creator.setOfUrl(request.getOnlyfansUrl() != null ? request.getOnlyfansUrl() : "");
            creator.setOnlyfansApiKey(request.getOnlyfansApiKey());
            creator.setOnlyfansAccountId(request.getOnlyfansAccountId());
            creator.setTone(request.getTone());
            creator.setTrackingCode(request.getTrackingCode());
            creator.setCreatedAt(LocalDateTime.now());

            Creator saved = creatorService.saveCreator(creator);

            String webhookUrl = serverBaseUrl + "/api/webhook/onlyfans";

            log.info("Successfully onboarded creator: {} with ID: {}", saved.getName(), saved.getCreatorId());

            return ResponseEntity.ok(new CreatorOnboardingResponse(
                true,
                "Creator onboarded successfully! Configure your OnlyFans webhook to: " + webhookUrl,
                saved.getCreatorId(),
                webhookUrl
            ));

        } catch (Exception e) {
            log.error("Error onboarding creator", e);
            return ResponseEntity.status(500).body(
                new CreatorOnboardingResponse(false, "Failed to onboard creator: " + e.getMessage(), null, null)
            );
        }
    }

    @GetMapping
    public ResponseEntity<List<CreatorSummary>> listCreators() {
        try {
            List<Creator> creators = creatorService.getAllCreators();
            List<CreatorSummary> summaries = creators.stream()
                .map(c -> new CreatorSummary(
                    c.getId(),
                    c.getCreatorId(),
                    c.getName(),
                    c.getOfUrl(),
                    c.getOnlyfansAccountId(),
                    c.getCreatedAt()
                ))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("Error listing creators", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{creatorId}")
    public ResponseEntity<Creator> getCreator(@PathVariable String creatorId) {
        Optional<Creator> creator = creatorService.findByCreatorId(creatorId);
        return creator.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{creatorId}")
    public ResponseEntity<CreatorOnboardingResponse> updateCreator(
            @PathVariable String creatorId,
            @RequestBody CreatorOnboardingRequest request) {
        try {
            Optional<Creator> existing = creatorService.findByCreatorId(creatorId);
            
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Creator creator = existing.get();
            
            if (request.getName() != null) {
                creator.setName(request.getName());
            }
            if (request.getOnlyfansUrl() != null) {
                creator.setOfUrl(request.getOnlyfansUrl());
            }
            if (request.getOnlyfansApiKey() != null) {
                creator.setOnlyfansApiKey(request.getOnlyfansApiKey());
            }
            if (request.getTone() != null) {
                creator.setTone(request.getTone());
            }
            if (request.getTrackingCode() != null) {
                creator.setTrackingCode(request.getTrackingCode());
            }

            Creator updated = creatorService.saveCreator(creator);

            log.info("Updated creator: {}", updated.getName());

            return ResponseEntity.ok(new CreatorOnboardingResponse(
                true,
                "Creator updated successfully",
                updated.getCreatorId(),
                null
            ));

        } catch (Exception e) {
            log.error("Error updating creator", e);
            return ResponseEntity.status(500).body(
                new CreatorOnboardingResponse(false, "Failed to update creator: " + e.getMessage(), null, null)
            );
        }
    }

    @DeleteMapping("/{creatorId}")
    public ResponseEntity<CreatorOnboardingResponse> deleteCreator(@PathVariable String creatorId) {
        try {
            Optional<Creator> existing = creatorService.findByCreatorId(creatorId);
            
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            creatorService.deleteCreator(existing.get());

            log.info("Deleted creator: {}", creatorId);

            return ResponseEntity.ok(new CreatorOnboardingResponse(
                true,
                "Creator deleted successfully",
                creatorId,
                null
            ));

        } catch (Exception e) {
            log.error("Error deleting creator", e);
            return ResponseEntity.status(500).body(
                new CreatorOnboardingResponse(false, "Failed to delete creator: " + e.getMessage(), null, null)
            );
        }
    }

    @Data
    @AllArgsConstructor
    public static class CreatorSummary {
        private Long id;
        private String creatorId;
        private String name;
        private String onlyfansUrl;
        private String accountId;
        private LocalDateTime createdAt;
    }
}
