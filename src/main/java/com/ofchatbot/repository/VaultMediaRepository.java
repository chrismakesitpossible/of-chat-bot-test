package com.ofchatbot.repository;

import com.ofchatbot.entity.VaultMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VaultMediaRepository extends JpaRepository<VaultMedia, Long> {
    List<VaultMedia> findByContentVaultId(Long contentVaultId);

    Optional<VaultMedia> findByContentVaultIdAndMediaId(Long contentVaultId, String mediaId);

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    VaultMedia findRandomUnpurchasedMedia(@Param("vaultId") Long vaultId, @Param("fanId") Long fanId);

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "AND type = :mediaType " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findRandomUnpurchasedMediaByType(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("mediaType") String mediaType,
        @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findRandomUnpurchasedMediaMultiple(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "AND (tags LIKE CONCAT('%', :interest, '%') OR categories LIKE CONCAT('%', :interest, '%')) " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findRandomUnpurchasedMediaByInterest(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("interest") String interest,
        @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "AND type = 'video' " +
           "AND (duration IS NULL OR duration <= :maxDuration) " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findShortVideos(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("maxDuration") int maxDuration,
        @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "AND type = 'video' " +
           "AND duration >= :minDuration " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findLongVideos(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("minDuration") int minDuration,
        @Param("limit") int limit
    );

    @Query(value = "SELECT * FROM vault_media WHERE content_vault_id = :vaultId " +
           "AND media_id NOT IN (SELECT media_id FROM fan_purchases WHERE fan_id = :fanId) " +
           "AND type = 'photo' " +
           "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<VaultMedia> findPhotos(
        @Param("vaultId") Long vaultId,
        @Param("fanId") Long fanId,
        @Param("limit") int limit
    );
}

