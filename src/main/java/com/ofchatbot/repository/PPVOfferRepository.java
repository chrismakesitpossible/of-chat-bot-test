package com.ofchatbot.repository;

import com.ofchatbot.entity.PPVOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PPVOfferRepository extends JpaRepository<PPVOffer, Long> {
    List<PPVOffer> findByFanIdAndPurchasedFalse(Long fanId);

    long countByFanId(Long fanId);
    
    List<PPVOffer> findByFanIdAndPurchasedTrue(Long fanId);

    @Query("SELECT COUNT(po) FROM PPVOffer po WHERE po.fanId = :fanId AND po.purchased = true AND po.purchasedAt >= :since")
    long countPurchasesByFanIdSince(@Param("fanId") Long fanId, @Param("since") LocalDateTime since);

    @Query("""
           SELECT po FROM PPVOffer po
           WHERE po.purchased = false
             AND po.followUpCount < 1
             AND po.sentAt < :cutoffTime
             AND po.sentAt = (
                 SELECT MAX(po2.sentAt) FROM PPVOffer po2
                 WHERE po2.fanId = po.fanId
                   AND po2.purchased = false
             )
           """)
    List<PPVOffer> findOffersNeedingFollowUp(@Param("cutoffTime") LocalDateTime cutoffTime);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PPVOffer o SET o.followUpCount = 1 WHERE o.fanId = :fanId AND o.purchased = false AND o.id <> :excludeId")
    void markOtherUnpurchasedFollowUpDone(@Param("fanId") Long fanId, @Param("excludeId") Long excludeId);
}
