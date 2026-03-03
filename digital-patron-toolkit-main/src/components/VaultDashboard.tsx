import { useState } from "react";
import { motion } from "framer-motion";
import {
  Download,
  Image,
  Video,
  Music,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Loader2,
  FolderOpen,
  HardDrive,
} from "lucide-react";

interface VaultMediaItem {
  id: number;
  type: "photo" | "video" | "audio";
  isReady: boolean;
  hasError: boolean;
  createdAt: string;
  preview?: string;
}

// Mock data for frontend demo
const mockMedia: VaultMediaItem[] = Array.from({ length: 12 }, (_, i) => ({
  id: 1000 + i,
  type: (["photo", "video", "photo", "audio", "video", "photo"][i % 6]) as VaultMediaItem["type"],
  isReady: Math.random() > 0.15,
  hasError: Math.random() > 0.9,
  createdAt: new Date(Date.now() - Math.random() * 30 * 86400000).toISOString(),
}));

const typeIcons = { photo: Image, video: Video, audio: Music };
const typeColors = {
  photo: "text-blue-400",
  video: "text-purple-400",
  audio: "text-green-400",
};

interface VaultDashboardProps {
  creatorName: string;
}

const VaultDashboard = ({ creatorName }: VaultDashboardProps) => {
  const [media] = useState<VaultMediaItem[]>(mockMedia);
  const [downloading, setDownloading] = useState(false);
  const [downloadResult, setDownloadResult] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "photo" | "video" | "audio">("all");

  const filtered = filter === "all" ? media : media.filter((m) => m.type === filter);
  const stats = {
    total: media.length,
    photos: media.filter((m) => m.type === "photo").length,
    videos: media.filter((m) => m.type === "video").length,
    audio: media.filter((m) => m.type === "audio").length,
    ready: media.filter((m) => m.isReady && !m.hasError).length,
  };

  const handleDownloadAll = async () => {
    setDownloading(true);
    setDownloadResult(null);
    await new Promise((r) => setTimeout(r, 3000));
    setDownloading(false);
    setDownloadResult(
      `Download complete: ${stats.ready} successful, ${stats.total - stats.ready} skipped.`
    );
  };

  const filters = [
    { key: "all" as const, label: "All", count: stats.total },
    { key: "photo" as const, label: "Photos", count: stats.photos },
    { key: "video" as const, label: "Videos", count: stats.videos },
    { key: "audio" as const, label: "Audio", count: stats.audio },
  ];

  return (
    <div className="w-full max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{creatorName}'s Vault</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {stats.total} items · {stats.ready} ready for download
          </p>
        </div>
        <button
          onClick={handleDownloadAll}
          disabled={downloading}
          className="flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:opacity-90 glow-primary transition-all disabled:opacity-50 self-start"
        >
          {downloading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Downloading...
            </>
          ) : (
            <>
              <Download className="w-4 h-4" />
              Download All
            </>
          )}
        </button>
      </div>

      {/* Download result */}
      {downloadResult && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center gap-3 p-4 rounded-xl bg-success/10 border border-success/20 text-success mb-6"
        >
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{downloadResult}</p>
        </motion.div>
      )}

      {/* Stats cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
        {[
          { icon: FolderOpen, label: "Total Items", value: stats.total, color: "text-foreground" },
          { icon: Image, label: "Photos", value: stats.photos, color: "text-blue-400" },
          { icon: Video, label: "Videos", value: stats.videos, color: "text-purple-400" },
          { icon: HardDrive, label: "Ready", value: stats.ready, color: "text-primary" },
        ].map((stat) => (
          <div key={stat.label} className="p-4 rounded-xl bg-card border border-border">
            <stat.icon className={`w-5 h-5 ${stat.color} mb-2`} />
            <p className="text-2xl font-bold text-foreground">{stat.value}</p>
            <p className="text-xs text-muted-foreground">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex items-center gap-2 mb-6">
        {filters.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              filter === f.key
                ? "bg-primary text-primary-foreground"
                : "bg-secondary text-muted-foreground hover:text-foreground"
            }`}
          >
            {f.label}
            <span className="ml-1.5 opacity-60">{f.count}</span>
          </button>
        ))}
        <button className="ml-auto p-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Media grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {filtered.map((item, i) => {
          const Icon = typeIcons[item.type];
          return (
            <motion.div
              key={item.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.03 }}
              className="group p-4 rounded-xl bg-card border border-border hover:border-primary/30 transition-all"
            >
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Icon className={`w-4 h-4 ${typeColors[item.type]}`} />
                  <span className="text-xs font-mono text-muted-foreground">#{item.id}</span>
                </div>
                {item.hasError ? (
                  <span className="flex items-center gap-1 text-xs text-destructive">
                    <AlertCircle className="w-3 h-3" />
                    Error
                  </span>
                ) : item.isReady ? (
                  <span className="flex items-center gap-1 text-xs text-success">
                    <CheckCircle2 className="w-3 h-3" />
                    Ready
                  </span>
                ) : (
                  <span className="text-xs text-muted-foreground">Processing</span>
                )}
              </div>
              <div className="h-28 rounded-lg bg-secondary/50 flex items-center justify-center mb-3">
                <Icon className={`w-8 h-8 ${typeColors[item.type]} opacity-30`} />
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted-foreground capitalize">{item.type}</span>
                <span className="text-xs text-muted-foreground">
                  {new Date(item.createdAt).toLocaleDateString()}
                </span>
              </div>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
};

export default VaultDashboard;
