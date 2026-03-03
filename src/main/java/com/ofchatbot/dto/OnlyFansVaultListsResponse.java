package com.ofchatbot.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnlyFansVaultListsResponse {
    private VaultListData data;
    private Meta _meta;

    @Data
    public static class VaultListData {
        private List<VaultList> list;
        private VaultStats all;
        private boolean hasMore;
        private boolean canCreateVaultLists;
        private String order;
        private String sort;
    }

    @Data
    public static class VaultList {
        private Long id;
        private String type;
        private String name;
        private boolean hasMedia;
        private boolean canUpdate;
        private boolean canDelete;
        private int videosCount;
        private int photosCount;
        private int gifsCount;
        private int audiosCount;
        private List<MediaItem> medias;
    }

    @Data
    public static class VaultStats {
        private int videosCount;
        private int photosCount;
        private int gifsCount;
        private int audiosCount;
        private List<MediaItem> medias;
    }

    @Data
    public static class MediaItem {
        private String type;
        private String url;
    }

    @Data
    public static class Meta {
        private Credits _credits;
        private Cache _cache;
        private RateLimits _rate_limits;
    }

    @Data
    public static class Credits {
        private int used;
        private int balance;
        private String note;
    }

    @Data
    public static class Cache {
        private boolean is_cached;
        private String note;
    }

    @Data
    public static class RateLimits {
        private int limit_minute;
        private int limit_day;
        private int remaining_minute;
        private int remaining_day;
    }
}
