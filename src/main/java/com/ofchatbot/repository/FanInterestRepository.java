package com.ofchatbot.repository;

import com.ofchatbot.entity.FanInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FanInterestRepository extends JpaRepository<FanInterest, Long> {
    List<FanInterest> findByFanId(Long fanId);
    
    Optional<FanInterest> findByFanIdAndCategory(Long fanId, String category);
    
    @Query("SELECT fi FROM FanInterest fi WHERE fi.fanId = :fanId ORDER BY fi.mentionCount DESC, fi.lastMentioned DESC")
    List<FanInterest> findTopInterestsByFanId(@Param("fanId") Long fanId);
    
    List<FanInterest> findByFanIdOrderByMentionCountDesc(Long fanId);
}
