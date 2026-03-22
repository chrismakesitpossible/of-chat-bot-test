package com.ofchatbot.service;

import com.ofchatbot.entity.Creator;
import com.ofchatbot.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorService {

    private final CreatorRepository creatorRepository;

    @Value("${creator.name}")
    private String defaultCreatorName;

    @Value("${creator.onlyfans.url}")
    private String defaultOnlyFansUrl;

    @Value("${onlyfans.api.key}")
    private String defaultApiKey;

    @Value("${onlyfans.account.id}")
    private String defaultAccountId;

    /** Ensure the default creator exists on startup (before vault sync runs). */
    @PostConstruct
    public void initDefaultCreator() {
        if (defaultAccountId == null || defaultAccountId.isBlank()) return;
        getOrCreateCreator(defaultAccountId);
    }

    public Creator getOrCreateCreator(String accountId) {
        // Webhook account_id is the same as creator_id; look up by both to handle existing rows
        Optional<Creator> existingCreator = creatorRepository.findByCreatorId(accountId)
            .or(() -> creatorRepository.findByOnlyfansAccountId(accountId));

        if (existingCreator.isPresent()) {
            return existingCreator.get();
        }

        Creator newCreator = new Creator();
        newCreator.setCreatorId(accountId);
        newCreator.setName(defaultCreatorName);
        newCreator.setOfUrl(defaultOnlyFansUrl);
        newCreator.setOnlyfansApiKey(defaultApiKey);
        newCreator.setOnlyfansAccountId(accountId);
        newCreator.setCreatedAt(LocalDateTime.now());

        Creator saved = creatorRepository.save(newCreator);
        log.info("Created new creator with account ID: {}", accountId);
        return saved;
    }

    public Optional<Creator> findByCreatorId(String creatorId) {
        return creatorRepository.findByCreatorId(creatorId);
    }

    public Optional<Creator> findByOnlyfansAccountId(String accountId) {
        return creatorRepository.findByOnlyfansAccountId(accountId);
    }

    public Creator saveCreator(Creator creator) {
        return creatorRepository.save(creator);
    }


    public List<Creator> getAllCreators() {
        return creatorRepository.findAll();
    }

    public void deleteCreator(Creator creator) {
        creatorRepository.delete(creator);
    }
}
