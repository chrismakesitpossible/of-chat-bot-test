package com.ofchatbot.repository;

import com.ofchatbot.entity.FanScriptProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FanScriptProgressRepository extends JpaRepository<FanScriptProgress, Long> {

    Optional<FanScriptProgress> findByFanIdAndScriptId(Long fanId, String scriptId);
}
