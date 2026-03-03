package com.ofchatbot.repository;

import com.ofchatbot.entity.CustomRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomRequestRepository extends JpaRepository<CustomRequest, Long> {
    List<CustomRequest> findByFanId(Long fanId);
    List<CustomRequest> findByStatus(String status);
}
