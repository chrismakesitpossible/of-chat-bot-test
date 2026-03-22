package com.ofchatbot.repository;

import com.ofchatbot.entity.Fan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FanRepository extends JpaRepository<Fan, Long> {
    Optional<Fan> findByContactId(String contactId);
    Optional<Fan> findByIgUsername(String igUsername);
    Optional<Fan> findByOnlyfansUserId(String onlyfansUserId);
    boolean existsByContactId(String contactId);
    Optional<Fan> findByOnlyfansUsername(String onlyfansUsername);
    List<Fan> findByLastUpdatedBeforeAndOnlyfansUserIdIsNotNull(LocalDateTime inactiveSince);
    List<Fan> findByLastUpdatedBetweenAndOnlyfansUserIdIsNotNull(LocalDateTime from, LocalDateTime to);
}
