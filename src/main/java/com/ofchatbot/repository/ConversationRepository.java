package com.ofchatbot.repository;

import com.ofchatbot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByContactId(String contactId);
    Optional<Conversation> findByConversationId(String conversationId);
    Optional<Conversation> findByFanIdAndPlatform(Long fanId, String platform);
    List<Conversation> findByFanId(Long fanId);
}
