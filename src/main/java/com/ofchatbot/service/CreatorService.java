package com.ofchatbot.service;

import com.ofchatbot.entity.Creator;
import com.ofchatbot.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    
    public Creator getOrCreateCreator(String accountId) {
        Optional<Creator> existingCreator = creatorRepository.findByCreatorId(accountId);
        
        if (existingCreator.isPresent()) {
            return existingCreator.get();
        }
        
        Creator newCreator = new Creator();
        newCreator.setCreatorId(accountId);
        newCreator.setName(defaultCreatorName);
        newCreator.setOfUrl(defaultOnlyFansUrl);
        newCreator.setCreatedAt(LocalDateTime.now());
        
        Creator saved = creatorRepository.save(newCreator);
        log.info("Created new creator with ID: {}", accountId);
        return saved;
    }
    
    public Optional<Creator> findByCreatorId(String creatorId) {
        return creatorRepository.findByCreatorId(creatorId);
    }
    
    public Creator saveCreator(Creator creator) {
        return creatorRepository.save(creator);
    }
}
