package com.ofchatbot.controller;

import com.ofchatbot.dto.VaultMediaItem;
import com.ofchatbot.service.ContentVaultService;
import com.ofchatbot.service.VaultDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@Slf4j
public class VaultDownloadController {

    private final VaultDownloadService vaultDownloadService;
    private final ContentVaultService contentVaultService;

    @GetMapping("/media")
    public ResponseEntity<Map<String, Object>> getAllVaultMedia() {
        log.info("Received request to list all vault media");
        
        try {
            List<VaultMediaItem> media = vaultDownloadService.getAllVaultMedia();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", media.size());
            response.put("media", media);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve vault media", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadAllVaultContent() {
        
        log.info("Received request to download all vault content");
        
        try {
            String result = vaultDownloadService.downloadAllVaultContent();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to download vault content", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Debug: list vault folders from OnlyFans API and S01 vaults in DB.
     * GET /api/vault/debug/lists — verify S01 - Lv.0 .. Lv.7 folders exist on OnlyFans and are synced.
     */
    @GetMapping("/debug/lists")
    public ResponseEntity<Map<String, Object>> getVaultListsDebug() {
        try {
            Map<String, Object> debug = contentVaultService.getVaultListsDebug();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", debug);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get vault lists debug", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Map<String, Object>> getVaultMediaById(@PathVariable Long mediaId) {
        log.info("Received request to get vault media: {}", mediaId);
        
        try {
            VaultMediaItem media = vaultDownloadService.getVaultMediaById(mediaId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("media", media);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve vault media: {}", mediaId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
