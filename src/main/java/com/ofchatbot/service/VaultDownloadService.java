package com.ofchatbot.service;

import com.ofchatbot.dto.VaultMediaItem;
import com.ofchatbot.dto.VaultMediaListResponse;
import com.ofchatbot.dto.VaultListInfo;
import com.ofchatbot.dto.VaultListsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultDownloadService {

    private final RestTemplate restTemplate;
    private final ErrorLogService errorLogService;

    @Value("${onlyfans.api.key}")
    private String apiKey;

    @Value("${onlyfans.api.base-url}")
    private String baseUrl;

    @Value("${onlyfans.account.id}")
    private String accountId;

    public List<VaultMediaItem> getAllVaultMedia() {
        List<VaultMediaItem> allMedia = new ArrayList<>();
        
        try {
            log.info("Fetching all vault media from main endpoint");
            List<VaultMediaItem> mainVaultMedia = getVaultMediaFromMainEndpoint();
            allMedia.addAll(mainVaultMedia);
            log.info("Retrieved {} media items from main vault endpoint", mainVaultMedia.size());
            
            List<VaultListInfo> vaultLists = getAllVaultLists();
            log.info("Found {} custom vault lists/folders", vaultLists.size());
            
            for (VaultListInfo vaultList : vaultLists) {
                try {
                    List<VaultMediaItem> listMedia = getVaultMediaByList(vaultList.getId());
                    
                    for (VaultMediaItem item : listMedia) {
                        boolean alreadyExists = allMedia.stream()
                            .anyMatch(existing -> existing.getId().equals(item.getId()));
                        if (!alreadyExists) {
                            allMedia.add(item);
                        }
                    }
                    log.info("Retrieved {} media items from vault list: {}", listMedia.size(), vaultList.getName());
                } catch (Exception e) {
                    log.error("Failed to retrieve media from vault list: {}", vaultList.getName(), e);
                }
            }
            
            log.info("Retrieved total of {} unique vault media items", allMedia.size());
            return allMedia;
        } catch (Exception e) {
            log.error("Failed to retrieve vault media", e);
            errorLogService.logError(
                "VAULT_MEDIA_RETRIEVAL_FAILED",
                "Failed to retrieve vault media",
                e,
                null
            );
            throw new RuntimeException("Failed to retrieve vault media", e);
        }
    }
    
    private List<VaultMediaItem> getVaultMediaFromMainEndpoint() {
        int limit = 50;
        int offset = 0;
        boolean hasMore = true;
        List<VaultMediaItem> allMedia = new ArrayList<>();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        while (hasMore && offset < 5000) {
            String url = String.format("%s/%s/media/vault?limit=%d&offset=%d", 
                baseUrl, accountId, limit, offset);
            
            try {
                ResponseEntity<VaultMediaListResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, VaultMediaListResponse.class
                );
                
                if (response.getBody() != null && response.getBody().getData() != null) {
                    List<VaultMediaItem> pageMedia = response.getBody().getData().getList();
                    if (pageMedia != null && !pageMedia.isEmpty()) {
                        allMedia.addAll(pageMedia);
                        offset += limit;
                        hasMore = response.getBody().getData().getHasMore() != null 
                            && response.getBody().getData().getHasMore();
                    } else {
                        hasMore = false;
                    }
                } else {
                    hasMore = false;
                }
            } catch (Exception e) {
                log.error("Failed to retrieve vault media page at offset {}", offset, e);
                hasMore = false;
            }
        }
        
        return allMedia;
    }
    
    private List<VaultListInfo> getAllVaultLists() {
        String url = String.format("%s/%s/media/vault/lists", baseUrl, accountId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<VaultListsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, VaultListsResponse.class
            );
            
            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData().getList();
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to retrieve vault lists", e);
            throw new RuntimeException("Failed to retrieve vault lists", e);
        }
    }
    
    private List<VaultMediaItem> getVaultMediaByList(Long listId) {
        int limit = 50;
        int offset = 0;
        boolean hasMore = true;
        List<VaultMediaItem> allMedia = new ArrayList<>();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        while (hasMore && offset < 5000) {
            String url = String.format("%s/%s/media/vault?list=%d&limit=%d&offset=%d", 
                baseUrl, accountId, listId, limit, offset);
            
            try {
                ResponseEntity<VaultMediaListResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, VaultMediaListResponse.class
                );
                
                if (response.getBody() != null && response.getBody().getData() != null) {
                    List<VaultMediaItem> pageMedia = response.getBody().getData().getList();
                    if (pageMedia != null && !pageMedia.isEmpty()) {
                        allMedia.addAll(pageMedia);
                        offset += limit;
                        hasMore = response.getBody().getData().getHasMore() != null 
                            && response.getBody().getData().getHasMore();
                    } else {
                        hasMore = false;
                    }
                } else {
                    hasMore = false;
                }
            } catch (Exception e) {
                log.error("Failed to retrieve vault media page for list {} at offset {}", listId, offset, e);
                hasMore = false;
            }
        }
        
        return allMedia;
    }

    public VaultMediaItem getVaultMediaById(Long mediaId) {
        String url = String.format("%s/%s/media/vault/%d", baseUrl, accountId, mediaId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );
            log.info("Retrieved vault media item: {}", mediaId);
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve vault media item: {}", mediaId, e);
            throw new RuntimeException("Failed to retrieve vault media item", e);
        }
    }

    public String downloadAllVaultContent() {
        List<VaultMediaItem> allMedia = getAllVaultMedia();
        
        String userHome = System.getProperty("user.home");
        String downloadPath = userHome + File.separator + "Downloads" + File.separator + "OnlyFans_Vault";
        
        Path basePath = Paths.get(downloadPath);
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            log.error("Failed to create download directory: {}", downloadPath, e);
            throw new RuntimeException("Failed to create download directory", e);
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (VaultMediaItem media : allMedia) {
            if (!shouldDownloadMedia(media)) {
                continue;
            }
            
            try {
                downloadMediaItem(media, basePath);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to download media item: {}", media.getId(), e);
                failCount++;
            }
        }
        
        String summary = String.format("Download complete: %d successful, %d failed out of %d total items. Saved to: %s", 
            successCount, failCount, allMedia.size(), downloadPath);
        log.info(summary);
        return summary;
    }

    private boolean shouldDownloadMedia(VaultMediaItem media) {
        if (media.getType() == null || !media.getIsReady() || media.getHasError()) {
            return false;
        }
        return true;
    }

    private void downloadMediaItem(VaultMediaItem media, Path basePath) throws IOException {
        if (media.getFiles() == null) {
            log.warn("No files available for media item: {}", media.getId());
            return;
        }
        
        VaultMediaItem.MediaFile fileToDownload = media.getFiles().getFull();
        
        if (fileToDownload == null || fileToDownload.getUrl() == null) {
            log.warn("No URL available for media item: {}", media.getId());
            return;
        }
        
        String cdnUrl = fileToDownload.getUrl();
        String downloadUrl = String.format("%s/%s/media/download/%s", baseUrl, accountId, cdnUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        headers.set("Accept", "*/*");
        headers.set("Connection", "keep-alive");
        
        RequestCallback requestCallback = request -> {
            request.getHeaders().addAll(headers);
        };
        
        String fileName = generateFileName(media);
        Path filePath = basePath.resolve(fileName);
        
        // Use a more robust download method with retry logic
        ResponseExtractor<Void> responseExtractor = response -> {
            try (InputStream inputStream = response.getBody();
                 OutputStream outputStream = Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                
                outputStream.flush();
                log.info("Downloaded media item {} to {} ({} bytes)", media.getId(), filePath, totalBytesRead);
            }
            return null;
        };
        
        // Retry logic for transient failures
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                URI uri = new URI(downloadUrl);
                restTemplate.execute(uri, HttpMethod.GET, requestCallback, responseExtractor);
                return; // Success, exit retry loop
            } catch (Exception e) {
                log.warn("Download attempt {} failed for media item {}: {}", attempt, media.getId(), e.getMessage());
                
                if (attempt == maxRetries) {
                    log.error("Failed to download media after {} attempts: {}", maxRetries, cdnUrl, e);
                    errorLogService.logError(
                        "VAULT_MEDIA_DOWNLOAD_FAILED",
                        "Failed to download media item: " + media.getId(),
                        e,
                        "CDN URL: " + cdnUrl
                    );
                    throw new IOException("Failed to download media after " + maxRetries + " attempts", e);
                }
                
                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", ie);
                }
            }
        }
    }

    private String generateFileName(VaultMediaItem media) {
        String extension = getFileExtension(media.getType());
        return String.format("%d_%s_full.%s", 
            media.getId(), 
            media.getType(), 
            extension
        );
    }

    private String getFileExtension(String mediaType) {
        if (mediaType == null) {
            return "bin";
        }
        
        switch (mediaType.toLowerCase()) {
            case "photo":
                return "jpg";
            case "video":
                return "mp4";
            case "audio":
                return "mp3";
            default:
                return "bin";
        }
    }
}
