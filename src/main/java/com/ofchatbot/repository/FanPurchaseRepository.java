package com.ofchatbot.repository;

import com.ofchatbot.entity.FanPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FanPurchaseRepository extends JpaRepository<FanPurchase, Long> {
    List<FanPurchase> findByFanId(Long fanId);
    boolean existsByFanIdAndMediaId(Long fanId, String mediaId);
    
    @Query("SELECT COUNT(fp) FROM FanPurchase fp WHERE fp.fanId = :fanId")
    Long countPurchasesByFanId(@Param("fanId") Long fanId);
}
