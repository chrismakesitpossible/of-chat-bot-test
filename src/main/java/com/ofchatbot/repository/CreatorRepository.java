package com.ofchatbot.repository;

import com.ofchatbot.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CreatorRepository extends JpaRepository<Creator, Long> {
    Optional<Creator> findByCreatorId(String creatorId);
    boolean existsByCreatorId(String creatorId);
}
