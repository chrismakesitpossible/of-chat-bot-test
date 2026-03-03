package com.ofchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.dto.OnlyFansVaultListResponse;
import com.ofchatbot.dto.OnlyFansVaultListsResponse;
import com.ofchatbot.dto.OnlyFansAllVaultMediaResponse;
import com.ofchatbot.entity.ContentCategory;
import com.ofchatbot.entity.ContentVault;
import com.ofchatbot.entity.VaultMedia;
import com.ofchatbot.repository.ContentVaultRepository;
import com.ofchatbot.repository.FanPurchaseRepository;
import com.ofchatbot.repository.VaultMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentVaultService {

    private final ContentVaultRepository contentVaultRepository;
    private final VaultMediaRepository vaultMediaRepository;
    private final FanPurchaseRepository fanPurchaseRepository;
    private final RestTemplate restTemplate;
    private final ErrorLogService errorLogService;
    private final ContentCategoryResolver contentCategoryResolver;
    private final CreatorService creatorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${onlyfans.api.key}")
    private String apiKey;

    @Value("${onlyfans.api.base-url}")
    private String baseUrl;

    @Value("${onlyfans.account.id}")
    private String accountId;

    @Value("${vault.sync.on-startup:true}")
    private boolean vaultSyncOnStartup;

    /** Resolved creator_id for vault storage/lookup (Creator row with this onlyfans_account_id). */
    private String resolveVaultCreatorId() {
        return creatorService.findByOnlyfansAccountId(accountId)
            .map(c -> c.getCreatorId())
            .orElseGet(() -> {
                log.warn("No Creator found with onlyfans_account_id={}. Vaults will use accountId. Set onlyfans_account_id on your Creator row so vaults use creator_id.", accountId);
                return accountId;
            });
    }


    @PostConstruct
    public void syncVaultListsOnStartup() {
        if (!vaultSyncOnStartup) {
            log.info("Vault sync on startup disabled (vault.sync.on-startup=false)");
            return;
        }
        log.info("Scheduling vault lists synchronization on application startup (async)");
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vault-sync");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                syncVaultLists();
            } catch (Exception e) {
                log.warn("Vault sync failed (app started anyway): {}", e.getMessage());
            }
        });
    }

    public void syncVaultLists() {
        log.info("Syncing vault lists from OnlyFans API");

        try {
            List<OnlyFansVaultListsResponse.VaultList> vaultLists = fetchAllVaultLists();
            
            if (vaultLists.isEmpty()) {
                log.warn("No vault lists found in OnlyFans account");
                return;
            }

            for (OnlyFansVaultListsResponse.VaultList vaultListData : vaultLists) {
                try {
                    syncIndividualVaultList(vaultListData);
                } catch (Exception e) {
                    log.error("Failed to sync vault list {}", vaultListData.getId(), e);
                    errorLogService.logError(
                        "VAULT_SYNC_FAILED",
                        "Failed to sync vault list: " + vaultListData.getId(),
                        e,
                        "Vault Name: " + vaultListData.getName()
                    );
                }
            }

            String cid = resolveVaultCreatorId();
            long s01Count = contentVaultRepository.findByCreatorId(cid).stream()
                .filter(v -> "S01".equals(v.getScriptId()))
                .count();
            log.info("Successfully synced {} vault lists (S01 Shower script vaults: {})", vaultLists.size(), s01Count);
            if (s01Count == 0) {
                log.warn("No S01 Shower script vaults found. Create OnlyFans vault folders named exactly 'S01 - Lv.0 - Solo Shower - White Robe' through 'S01 - Lv.7 - Solo Shower - White Robe', then restart. You can run: python3 create_s01_shower_folders.py");
            }
        } catch (Exception e) {
            log.error("Failed to fetch vault lists from OnlyFans API", e);
            errorLogService.logError(
                "VAULT_LISTS_FETCH_FAILED",
                "Failed to fetch vault lists from OnlyFans API",
                e,
                null
            );
        }
    }

    private List<OnlyFansVaultListsResponse.VaultList> fetchAllVaultLists() {
        String url = String.format("%s/%s/media/vault/lists", baseUrl, accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<OnlyFansVaultListsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, OnlyFansVaultListsResponse.class
            );
            
            OnlyFansVaultListsResponse vaultListsResponse = response.getBody();
            if (vaultListsResponse != null && vaultListsResponse.getData() != null) {
                return vaultListsResponse.getData().getList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch all vault lists", e);
            throw e;
        }
    }

    /**
     * Debug: fetch vault list names from OnlyFans API and S01 vaults from DB.
     * Use GET /api/vault/debug/lists to verify folders exist on OnlyFans and are synced.
     */
    public Map<String, Object> getVaultListsDebug() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> apiFolderNames = new ArrayList<>();
        try {
            List<OnlyFansVaultListsResponse.VaultList> lists = fetchAllVaultLists();
            for (OnlyFansVaultListsResponse.VaultList v : lists) {
                if (v.getName() != null) {
                    apiFolderNames.add(v.getName() + " (id=" + v.getId() + ", photos=" + v.getPhotosCount() + ", videos=" + v.getVideosCount() + ")");
                }
            }
        } catch (Exception e) {
            out.put("onlyfans_api_error", e.getMessage());
        }
        out.put("onlyfans_api_folders", apiFolderNames);
        out.put("onlyfans_account_id_used", accountId);

        String cid = resolveVaultCreatorId();
        out.put("vault_creator_id_resolved", cid);

        List<ContentVault> s01Vaults = contentVaultRepository.findByCreatorId(cid).stream()
            .filter(v -> "S01".equals(v.getScriptId()))
            .sorted(java.util.Comparator.comparing(ContentVault::getLevel))
            .toList();
        List<Map<String, Object>> dbS01 = new ArrayList<>();
        for (ContentVault v : s01Vaults) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("creatorId", v.getCreatorId());
            row.put("scriptId", v.getScriptId());
            row.put("level", v.getLevel());
            row.put("name", v.getName());
            row.put("vaultListId", v.getVaultListId());
            row.put("mediaCount", v.getMediaCount());
            dbS01.add(row);
        }
        out.put("db_s01_vaults", dbS01);

        List<ContentVault> anyS01 = contentVaultRepository.findByCreatorId(cid).stream()
            .filter(v -> "S01".equals(v.getScriptId()))
            .toList();
        if (anyS01.isEmpty() && !apiFolderNames.isEmpty()) {
            out.put("hint", "API has folders but DB has no S01 vaults for creator " + cid + ". Run sync (restart app) and ensure Creator row has onlyfans_account_id=" + accountId + " so vaults are stored under creator_id.");
        }
        return out;
    }

    private void syncIndividualVaultList(OnlyFansVaultListsResponse.VaultList vaultListData) {
        Long vaultListId = vaultListData.getId();
        String vaultName = vaultListData.getName();
        ContentCategory category = contentCategoryResolver.fromFolderName(vaultName);

        ContentVault vault = contentVaultRepository.findByVaultListId(vaultListId)
            .orElse(new ContentVault());

        vault.setCreatorId(resolveVaultCreatorId());
        vault.setVaultListId(vaultListId);
        vault.setLevel(0);
        vault.setName(vaultName != null ? vaultName : "Unnamed");
        vault.setContentCategory(contentCategoryResolver.toDbValue(category));
        vault.setMediaCount(vaultListData.getVideosCount() + vaultListData.getPhotosCount());
        vault.setPhotosCount(vaultListData.getPhotosCount());
        vault.setVideosCount(vaultListData.getVideosCount());

        // Parse S01 - Lv.X - Solo Shower - White Robe → script_id = S01, level = X (0-7). Allow optional space after "Lv." (e.g. "Lv. 0").
        if (vaultName != null) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("S01\\s*-\\s*Lv\\.?\\s*(\\d)\\s*-", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(vaultName.trim());
            if (matcher.find()) {
                int scriptLevel = Integer.parseInt(matcher.group(1));
                if (scriptLevel >= 0 && scriptLevel <= 7) {
                    vault.setScriptId("S01");
                    vault.setLevel(scriptLevel);
                    log.info("Parsed S01 Shower Lv.{} from folder: {}", scriptLevel, vaultName);
                }
            }
        }

        vault = contentVaultRepository.save(vault);

        try {
            List<OnlyFansAllVaultMediaResponse.MediaItem> mediaItems = fetchVaultListMedia(vaultListId);
            if (!mediaItems.isEmpty()) {
                syncVaultMediaFromVaultEndpoint(vault.getId(), mediaItems);
                log.info("Synced vault list {} '{}' ({}): {} media items (via /media/vault list filter)",
                    vaultListId, vaultName != null ? vaultName : "Unnamed", category, mediaItems.size());
            } else {
                OnlyFansVaultListResponse detailedVaultData = fetchVaultList(vaultListId);
                if (detailedVaultData != null && detailedVaultData.getMedia() != null && !detailedVaultData.getMedia().isEmpty()) {
                    syncVaultMedia(vault.getId(), detailedVaultData.getMedia());
                    log.info("Synced vault list {} '{}' ({}): {} media items (via /lists/{id} inline media)",
                        vaultListId, vaultName != null ? vaultName : "Unnamed", category, detailedVaultData.getMedia().size());
                } else {
                    log.info("Synced vault list {} '{}' ({}): {} total media items ({} photos, {} videos) (no media list returned)",
                        vaultListId, vaultName != null ? vaultName : "Unnamed", category, vault.getMediaCount(), vault.getPhotosCount(), vault.getVideosCount());
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch detailed media for vault list {}, using basic counts: {} total items",
                vaultListId, vault.getMediaCount(), e);
        }
    }

    private OnlyFansVaultListResponse fetchVaultList(Long vaultListId) {
        String url = String.format("%s/%s/media/vault/lists/%d", baseUrl, accountId, vaultListId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<OnlyFansVaultListResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, OnlyFansVaultListResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch vault list {}", vaultListId, e);
            throw e;
        }
    }

    /**
     * Fetch media items for a specific vault list using the Media Vault endpoint:
     * GET /{account}/media/vault?list={vaultListId}&limit={limit}&offset={offset}
     *
     * We parse JSON to support multiple response shapes from the provider.
     */
    private List<OnlyFansAllVaultMediaResponse.MediaItem> fetchVaultListMedia(Long vaultListId) {
        int limit = 50;
        int offset = 0;
        boolean hasMore = true;

        List<OnlyFansAllVaultMediaResponse.MediaItem> all = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(headers);

        while (hasMore && offset < 5000) {
            String url = String.format("%s/%s/media/vault?list=%d&limit=%d&offset=%d", baseUrl, accountId, vaultListId, limit, offset);
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
                String body = response.getBody();
                if (body == null || body.isBlank()) break;

                JsonNode root = objectMapper.readTree(body);
                JsonNode data = root.has("data") ? root.get("data") : root;

                JsonNode listNode = data.has("list") ? data.get("list") : null;
                if (listNode == null || !listNode.isArray()) {
                    // Some shapes may use `data` or `items`
                    JsonNode alt = data.has("data") ? data.get("data") : (data.has("items") ? data.get("items") : null);
                    if (alt != null && alt.isArray()) listNode = alt;
                }

                int addedThisPage = 0;
                if (listNode != null && listNode.isArray()) {
                    for (JsonNode item : listNode) {
                        String id = item.path("id").asText(null);
                        if (id == null || id.isBlank()) continue;

                        OnlyFansAllVaultMediaResponse.MediaItem mediaItem = new OnlyFansAllVaultMediaResponse.MediaItem();
                        mediaItem.setId(id);
                        mediaItem.setType(item.path("type").asText(null));

                        String mediaUrl = item.path("url").asText(null);
                        if (mediaUrl == null || mediaUrl.isBlank()) {
                            mediaUrl = item.path("files").path("full").path("url").asText(null);
                        }
                        mediaItem.setUrl(mediaUrl);

                        if (!item.path("duration").isMissingNode() && !item.path("duration").isNull()) {
                            mediaItem.setDuration(item.path("duration").asInt());
                        }

                        all.add(mediaItem);
                        addedThisPage++;
                    }
                }

                Boolean hasMoreNode = null;
                if (data.has("hasMore")) {
                    hasMoreNode = data.get("hasMore").asBoolean();
                } else if (root.has("hasMore")) {
                    hasMoreNode = root.get("hasMore").asBoolean();
                }

                if (hasMoreNode != null) {
                    hasMore = hasMoreNode;
                } else {
                    // Fallback heuristic: if we got a full page, assume there could be more
                    hasMore = addedThisPage >= limit;
                }

                offset += limit;
            } catch (Exception e) {
                log.warn("Failed to fetch vault media page for list {} (offset={}, limit={})", vaultListId, offset, limit, e);
                break;
            }
        }

        return all;
    }

    private void syncVaultMedia(Long contentVaultId, List<OnlyFansVaultListResponse.MediaItem> mediaItems) {
        for (OnlyFansVaultListResponse.MediaItem item : mediaItems) {
            Optional<VaultMedia> existingOpt = vaultMediaRepository.findByContentVaultIdAndMediaId(contentVaultId, item.getId());

            VaultMedia media;
            if (existingOpt.isPresent()) {
                media = existingOpt.get();
            } else {
                media = new VaultMedia();
                media.setContentVaultId(contentVaultId);
                media.setMediaId(item.getId());
            }

            media.setType(item.getType());
            media.setUrl(item.getUrl());
            media.setDuration(item.getDuration());

            vaultMediaRepository.save(media);

            if (item.getDuration() != null) {
                log.debug("Synced {} media {} with duration: {}s", item.getType(), item.getId(), item.getDuration());
            }
        }
    }

    private void syncVaultMediaFromVaultEndpoint(Long contentVaultId, List<OnlyFansAllVaultMediaResponse.MediaItem> mediaItems) {
        for (OnlyFansAllVaultMediaResponse.MediaItem item : mediaItems) {
            Optional<VaultMedia> existingOpt = vaultMediaRepository.findByContentVaultIdAndMediaId(contentVaultId, item.getId());

            VaultMedia media;
            if (existingOpt.isPresent()) {
                media = existingOpt.get();
            } else {
                media = new VaultMedia();
                media.setContentVaultId(contentVaultId);
                media.setMediaId(item.getId());
            }

            if (item.getType() != null) {
                media.setType(item.getType());
            } else {
                media.setType("unknown");
            }
            media.setUrl(item.getUrl());
            media.setDuration(item.getDuration());

            vaultMediaRepository.save(media);
        }
    }

    /** Step 1: One random unpurchased teaser from SOLO or TEASER vault (first offer is always free). */
    public VaultMedia getTeaserMedia(Long fanId) {
        String cid = resolveVaultCreatorId();
        for (String cat : List.of(ContentCategory.TEASER.name(), ContentCategory.SOLO.name())) {
            List<ContentVault> vaults = contentVaultRepository.findByCreatorIdAndContentCategory(cid, cat);
            for (ContentVault vault : vaults) {
                VaultMedia media = vaultMediaRepository.findRandomUnpurchasedMedia(vault.getId(), fanId);
                if (media != null) {
                    log.info("Teaser media for fan {} from vault {} ({})", fanId, vault.getName(), cat);
                    return media;
                }
            }
        }
        log.warn("No teaser media found for fan {}", fanId);
        return null;
    }

    /**
     * Step 1: Media for a given price tier and category. Bundle size by price: 9.95=1, 29.95=3–4, 49.95=5–7, etc.
     * Uses SOLO as default category if no vault for requested category.
     */
    public List<VaultMedia> getMediaForPriceTier(Long fanId, ContentCategory category, double price) {
        String cid = resolveVaultCreatorId();
        List<ContentVault> vaults = contentVaultRepository.findByCreatorIdAndContentCategory(cid, category.name());
        if (vaults.isEmpty() && category != ContentCategory.SOLO) {
            vaults = contentVaultRepository.findByCreatorIdAndContentCategory(cid, ContentCategory.SOLO.name());
        }
        if (vaults.isEmpty()) {
            log.warn("No vaults for category {} (or SOLO fallback)", category);
            return List.of();
        }
        int wantItems = itemsForPrice(price);
        List<VaultMedia> bundle = new java.util.ArrayList<>();
        for (ContentVault vault : vaults) {
            if (bundle.size() >= wantItems) break;
            int need = wantItems - bundle.size();
            List<VaultMedia> more = vaultMediaRepository.findRandomUnpurchasedMediaMultiple(vault.getId(), fanId, need);
            bundle.addAll(more);
        }
        if (bundle.isEmpty()) {
            log.warn("No unpurchased media for fan {} at price {} ({})", fanId, price, category);
        } else {
            log.info("Bundle for fan {} at ${}: {} items ({})", fanId, price, bundle.size(), category);
        }
        return bundle;
    }

    /**
     * Media for a given price tier and category, biased by fan interests (tags like "shower", "feet", "couple").
     * Uses VaultMedia.tags/categories via VaultMediaRepository.findRandomUnpurchasedMediaByInterest.
     * Falls back to getMediaForPriceTier when no interest-based media is found.
     */
    public List<VaultMedia> getMediaForPriceTierPersonalized(Long fanId, ContentCategory category, double price, List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return getMediaForPriceTier(fanId, category, price);
        }

        String cid = resolveVaultCreatorId();
        List<ContentVault> vaults = contentVaultRepository.findByCreatorIdAndContentCategory(cid, category.name());
        if (vaults.isEmpty() && category != ContentCategory.SOLO) {
            vaults = contentVaultRepository.findByCreatorIdAndContentCategory(cid, ContentCategory.SOLO.name());
        }
        if (vaults.isEmpty()) {
            log.warn("No vaults for category {} (or SOLO fallback)", category);
            return List.of();
        }

        int wantItems = itemsForPrice(price);
        List<VaultMedia> bundle = new ArrayList<>();

        for (ContentVault vault : vaults) {
            if (bundle.size() >= wantItems) break;
            int need = wantItems - bundle.size();
            if (need <= 0) break;

            // Try to fill with interest-based media first.
            for (String interest : interests) {
                if (need <= 0) break;
                List<VaultMedia> interestMedia = vaultMediaRepository.findRandomUnpurchasedMediaByInterest(
                    vault.getId(), fanId, interest, need
                );
                if (!interestMedia.isEmpty()) {
                    bundle.addAll(interestMedia);
                    need = wantItems - bundle.size();
                    if (bundle.size() >= wantItems) break;
                }
            }

            // If still short, fill remaining slots from random unpurchased media.
            if (need > 0) {
                List<VaultMedia> more = vaultMediaRepository.findRandomUnpurchasedMediaMultiple(vault.getId(), fanId, need);
                bundle.addAll(more);
            }
        }

        if (bundle.isEmpty()) {
            log.warn("No personalized media for fan {} at price {} ({}) with interests {}", fanId, price, category, String.join(", ", interests));
        } else {
            log.info("Personalized bundle for fan {} at ${}: {} items ({}) with interests {}",
                fanId, price, bundle.size(), category, String.join(", ", interests));
        }

        return bundle;
    }

    private int itemsForPrice(double price) {
        if (price <= 0) return 1;
        if (price <= 10) return 1;
        if (price <= 35) return 4;
        if (price <= 75) return 6;
        if (price <= 125) return 10;
        if (price <= 175) return 12;
        return 15;
    }

    public VaultMedia getMediaForLevel(Integer level, Long fanId) {
        String cid = resolveVaultCreatorId();
        Optional<ContentVault> vaultOpt = contentVaultRepository.findByCreatorIdAndLevel(cid, level);
        if (vaultOpt.isEmpty()) {
            log.warn("No vault found for level {}", level);
            return null;
        }
        ContentVault vault = vaultOpt.get();
        VaultMedia media = vaultMediaRepository.findRandomUnpurchasedMedia(vault.getId(), fanId);

        if (media == null) {
            log.warn("No unpurchased media found for fan {} at level {}", fanId, level);
        }

        return media;
    }

    /**
     * All unpurchased media from the vault for the given script + level (e.g. S01 Lv.0–Lv.7).
     * Used by Shower script to send content from the correct folder.
     * creatorId must be the Creator entity's creator_id (vaults are stored under that id at sync).
     * If no vault is found for the given creatorId, tries the resolved vault creator (same as sync) to handle creator_id vs onlyfans_account_id mismatch.
     */
    public List<VaultMedia> getMediaForScriptLevel(String creatorId, String scriptId, int level, Long fanId) {
        String resolvedCid = resolveVaultCreatorId();
        Optional<ContentVault> vaultOpt = contentVaultRepository.findByCreatorIdAndScriptIdAndLevel(creatorId, scriptId, level);
        if (vaultOpt.isEmpty() && !resolvedCid.equals(creatorId)) {
            vaultOpt = contentVaultRepository.findByCreatorIdAndScriptIdAndLevel(resolvedCid, scriptId, level);
            if (vaultOpt.isPresent()) {
                log.info("Resolved script {} level {} vault using vault creator id {} (fan creator was {})", scriptId, level, resolvedCid, creatorId);
            }
        }
        if (vaultOpt.isEmpty()) {
            log.warn("No vault found for script {} level {} (creator {}; also tried vault creator {})", scriptId, level, creatorId, resolvedCid);
            return List.of();
        }
        ContentVault vault = vaultOpt.get();
        List<VaultMedia> list = vaultMediaRepository.findRandomUnpurchasedMediaMultiple(vault.getId(), fanId, 20);
        if (!list.isEmpty()) {
            log.info("Script {} level {}: {} unpurchased items for fan {}", scriptId, level, list.size(), fanId);
        }
        return list;
    }

    public List<VaultMedia> getMediaBundleForLevel(Integer level, Long fanId) {
        BundleConfig config = getBundleConfigForLevel(level);

        String cid = resolveVaultCreatorId();
        Optional<ContentVault> vaultOpt = contentVaultRepository.findByCreatorIdAndLevel(cid, level);
        if (vaultOpt.isEmpty()) {
            log.warn("No vault found for level {}", level);
            return List.of();
        }
        ContentVault vault = vaultOpt.get();
        List<VaultMedia> bundle = new java.util.ArrayList<>();

        List<VaultMedia> photos = vaultMediaRepository.findPhotos(
            vault.getId(), fanId, config.photoCount
        );
        bundle.addAll(photos);

        List<VaultMedia> videos;
        if (level >= 4) {
            videos = vaultMediaRepository.findLongVideos(
                vault.getId(), fanId, config.minVideoDuration, config.videoCount
            );
            log.info("Fetching LONG videos (>= {}s) for level {}", config.minVideoDuration, level);
        } else if (level >= 2) {
            videos = vaultMediaRepository.findShortVideos(
                vault.getId(), fanId, config.maxVideoDuration, config.videoCount
            );
            log.info("Fetching SHORT videos (<= {}s) for level {}", config.maxVideoDuration, level);
        } else {
            videos = List.of();
        }
        bundle.addAll(videos);

        int remaining = config.totalItems - bundle.size();
        if (remaining > 0) {
            List<VaultMedia> additional = vaultMediaRepository.findRandomUnpurchasedMediaMultiple(
                vault.getId(), fanId, remaining
            );
            bundle.addAll(additional);
        }

        if (bundle.isEmpty()) {
            log.warn("No unpurchased media found for fan {} at level {}", fanId, level);
        } else {
            log.info("Created bundle for fan {} at level {}: {} items ({} photos, {} videos)",
                fanId, level, bundle.size(), photos.size(), videos.size());
        }

        return bundle;
    }

    public List<VaultMedia> getPersonalizedMediaBundle(Integer level, Long fanId, List<String> interests) {
        BundleConfig config = getBundleConfigForLevel(level);

        String cid = resolveVaultCreatorId();
        Optional<ContentVault> vaultOpt = contentVaultRepository.findByCreatorIdAndLevel(cid, level);
        if (vaultOpt.isEmpty()) {
            log.warn("No vault found for level {}", level);
            return List.of();
        }
        ContentVault vault = vaultOpt.get();
        List<VaultMedia> bundle = new java.util.ArrayList<>();

        if (!interests.isEmpty()) {
            for (String interest : interests) {
                List<VaultMedia> interestMedia = vaultMediaRepository.findRandomUnpurchasedMediaByInterest(
                    vault.getId(), fanId, interest, config.totalItems / 2
                );
                bundle.addAll(interestMedia);

                if (bundle.size() >= config.totalItems) {
                    break;
                }
            }

            log.info("Found {} personalized items for fan {} based on interests: {}",
                bundle.size(), fanId, String.join(", ", interests));
        }

        int photosNeeded = config.photoCount - (int) bundle.stream().filter(m -> "photo".equals(m.getType())).count();
        if (photosNeeded > 0) {
            List<VaultMedia> photos = vaultMediaRepository.findPhotos(
                vault.getId(), fanId, photosNeeded
            );
            bundle.addAll(photos);
        }

        int videosNeeded = config.videoCount - (int) bundle.stream().filter(m -> "video".equals(m.getType())).count();
        if (videosNeeded > 0) {
            List<VaultMedia> videos;
            if (level >= 4) {
                videos = vaultMediaRepository.findLongVideos(
                    vault.getId(), fanId, config.minVideoDuration, videosNeeded
                );
                log.info("Adding {} LONG videos (>= {}s) to personalized bundle for level {}",
                    videos.size(), config.minVideoDuration, level);
            } else if (level >= 2) {
                videos = vaultMediaRepository.findShortVideos(
                    vault.getId(), fanId, config.maxVideoDuration, videosNeeded
                );
                log.info("Adding {} SHORT videos (<= {}s) to personalized bundle for level {}",
                    videos.size(), config.maxVideoDuration, level);
            } else {
                videos = List.of();
            }
            bundle.addAll(videos);
        }

        int remaining = config.totalItems - bundle.size();
        if (remaining > 0) {
            List<VaultMedia> additional = vaultMediaRepository.findRandomUnpurchasedMediaMultiple(
                vault.getId(), fanId, remaining
            );
            bundle.addAll(additional);
        }

        if (bundle.isEmpty()) {
            log.warn("No personalized media found for fan {} at level {}", fanId, level);
        } else {
            long photoCount = bundle.stream().filter(m -> "photo".equals(m.getType())).count();
            long videoCount = bundle.stream().filter(m -> "video".equals(m.getType())).count();
            log.info("Created personalized bundle for fan {} at level {}: {} items ({} photos, {} videos)",
                fanId, level, bundle.size(), photoCount, videoCount);
        }

        return bundle;
    }

    private BundleConfig getBundleConfigForLevel(Integer level) {
        return switch (level) {
            case 1 -> new BundleConfig(1, 1, 0, 0, 0);
            case 2 -> new BundleConfig(1, 1, 0, 0, 60);
            case 3 -> new BundleConfig(4, 3, 1, 0, 90);
            case 4 -> new BundleConfig(7, 5, 2, 120, 0);
            case 5 -> new BundleConfig(10, 7, 3, 180, 0);
            case 6 -> new BundleConfig(20, 15, 5, 300, 0);
            default -> new BundleConfig(1, 1, 0, 0, 0);
        };
    }

    public boolean hasFanPurchased(Long fanId, String mediaId) {
        return fanPurchaseRepository.existsByFanIdAndMediaId(fanId, mediaId);
    }

    public boolean hasFanMadeAnyPurchase(Long fanId) {
        return fanPurchaseRepository.countPurchasesByFanId(fanId) > 0;
    }

    private static class BundleConfig {
        final int totalItems;
        final int photoCount;
        final int videoCount;
        final int minVideoDuration;
        final int maxVideoDuration;

        BundleConfig(int totalItems, int photoCount, int videoCount, int minVideoDuration, int maxVideoDuration) {
            this.totalItems = totalItems;
            this.photoCount = photoCount;
            this.videoCount = videoCount;
            this.minVideoDuration = minVideoDuration;
            this.maxVideoDuration = maxVideoDuration;
        }
    }
}
