package com.ofchatbot.repository;

import com.ofchatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;


@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByContactIdOrderByTimestampAsc(String contactId);
    List<Message> findTop10ByContactIdOrderByTimestampDesc(String contactId);
    java.util.Optional<Message> findByExternalMessageId(String externalMessageId);
}
