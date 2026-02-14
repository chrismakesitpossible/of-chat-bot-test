package com.ofchatbot.repository;

import com.ofchatbot.entity.ScriptPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptPerformanceRepository extends JpaRepository<ScriptPerformance, Long> {
    
    Optional<ScriptPerformance> findByCreatorIdAndScriptCategoryAndConversationState(
        String creatorId, String scriptCategory, String conversationState
    );
    
    List<ScriptPerformance> findByCreatorIdOrderByConversionRateDesc(String creatorId);
    
    List<ScriptPerformance> findByCreatorIdOrderByTotalRevenueGeneratedDesc(String creatorId);
    
    List<ScriptPerformance> findByCreatorIdOrderByAverageEngagementScoreDesc(String creatorId);
    
    @Query("SELECT sp FROM ScriptPerformance sp WHERE sp.creatorId = :creatorId " +
           "AND sp.lastUsedAt >= :since ORDER BY sp.conversionRate DESC")
    List<ScriptPerformance> findTopPerformingScripts(
        @Param("creatorId") String creatorId,
        @Param("since") LocalDateTime since
    );
    
    @Query("SELECT sp FROM ScriptPerformance sp WHERE sp.creatorId = :creatorId " +
           "AND sp.conversionRate < :threshold AND sp.timesUsed >= :minUsage")
    List<ScriptPerformance> findUnderperformingScripts(
        @Param("creatorId") String creatorId,
        @Param("threshold") Double threshold,
        @Param("minUsage") Integer minUsage
    );
    
    @Query("SELECT SUM(sp.totalRevenueGenerated) FROM ScriptPerformance sp WHERE sp.creatorId = :creatorId")
    Double getTotalRevenueByCreator(@Param("creatorId") String creatorId);
    
    @Query("SELECT AVG(sp.conversionRate) FROM ScriptPerformance sp WHERE sp.creatorId = :creatorId")
    Double getAverageConversionRateByCreator(@Param("creatorId") String creatorId);
}
