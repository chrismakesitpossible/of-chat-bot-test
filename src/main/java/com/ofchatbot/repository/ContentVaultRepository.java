package com.ofchatbot.repository;

import com.ofchatbot.entity.ContentVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentVaultRepository extends JpaRepository<ContentVault, Long> {
    List<ContentVault> findByCreatorId(String creatorId);
    List<ContentVault> findByCreatorIdAndContentCategory(String creatorId, String contentCategory);
    Optional<ContentVault> findByCreatorIdAndLevel(String creatorId, Integer level);
    Optional<ContentVault> findByCreatorIdAndScriptIdAndLevel(String creatorId, String scriptId, Integer level);
    Optional<ContentVault> findByVaultListId(Long vaultListId);
}
