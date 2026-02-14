package com.ofchatbot.service;

import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ConversationService {
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    public Conversation getOrCreateConversation(Fan fan) {
        Optional<Conversation> existingConversation = conversationRepository
            .findByFanIdAndPlatform(fan.getId(), "onlyfans");
        
        if (existingConversation.isPresent()) {
            return existingConversation.get();
        }
        
        Conversation conversation = new Conversation();
        conversation.setFanId(fan.getId());
        conversation.setPlatform("onlyfans");
        conversation.setCreatorId(fan.getCreatorId());
        conversation.setContactId(fan.getContactId());
        
        return conversationRepository.save(conversation);
    }
}
