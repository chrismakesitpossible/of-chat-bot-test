package com.ofchatbot.service;

import com.ofchatbot.entity.Fan;
import com.ofchatbot.repository.FanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FanService {
    
    private final FanRepository fanRepository;
    
    @Transactional
    public Fan createOrUpdateFan(String creatorId, String contactId, String igUsername) {
        Optional<Fan> existingFan = fanRepository.findByContactId(contactId);
        
        if (existingFan.isPresent()) {
            Fan fan = existingFan.get();
            fan.setMessageCount(fan.getMessageCount() + 1);
            fan.setLastUpdated(LocalDateTime.now());
            if (igUsername != null && !igUsername.isEmpty()) {
                fan.setIgUsername(igUsername);
            }
            log.info("Updated existing fan: {}", contactId);
            return fanRepository.save(fan);
        } else {
            Fan newFan = new Fan();
            newFan.setCreatorId(creatorId);
            newFan.setContactId(contactId);
            newFan.setIgUsername(igUsername);
            newFan.setMessageCount(1);
            newFan.setState("OPENING");
            newFan.setLastIntent("unknown");
            newFan.setLastUpdated(LocalDateTime.now());
            newFan.setCreatedAt(LocalDateTime.now());
            log.info("Created new fan: {}", contactId);
            return fanRepository.save(newFan);
        }
    }
    
    public Optional<Fan> findByContactId(String contactId) {
        return fanRepository.findByContactId(contactId);
    }
    
    public Optional<Fan> findByIgUsername(String igUsername) {
        return fanRepository.findByIgUsername(igUsername);
    }
    
    public Optional<Fan> findByOnlyfansUserId(String onlyfansUserId) {
        return fanRepository.findByOnlyfansUserId(onlyfansUserId);
    }
    
    public Fan saveFan(Fan fan) {
        return fanRepository.save(fan);
    }

    public Optional<Fan> findByOnlyfansUsername(String onlyfansUsername) {
        return fanRepository.findByOnlyfansUsername(onlyfansUsername);
    }

    public Optional<Fan> findByOnlyfansUsername(String onlyfansUsername) {
        return fanRepository.findByOnlyfansUsername(onlyfansUsername);
    }

    public List<Fan> findInactiveFans(LocalDateTime inactiveSince) {
        return fanRepository.findByLastUpdatedBeforeAndOnlyfansUserIdIsNotNull(inactiveSince);
    }
}
