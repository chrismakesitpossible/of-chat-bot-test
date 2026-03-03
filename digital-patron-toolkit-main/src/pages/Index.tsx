import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import OnboardingForm from "@/components/OnboardingForm";
import VaultDashboard from "@/components/VaultDashboard";
import OnboardingSuccess from "@/components/OnboardingSuccess";

const Index = () => {
  const [view, setView] = useState<"onboard" | "dashboard">("onboard");
  const [creatorName, setCreatorName] = useState("");
  const [creatorId, setCreatorId] = useState("");
  const [webhookUrl, setWebhookUrl] = useState("");

  return (
    <div className="min-h-screen bg-background">
      {/* Nav */}
      <nav className="sticky top-0 z-50 border-b border-border bg-background/80 backdrop-blur-xl">
        <div className="max-w-5xl mx-auto px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-primary flex items-center justify-center">
            </div>
            <span className="text-sm font-semibold text-foreground tracking-tight">CreatorSync</span>
          </div>
          <div className="flex items-center gap-1">
            {["onboard", "dashboard"].map((v) => (
              <button
                key={v}
                onClick={() => setView(v as "onboard" | "dashboard")}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all capitalize ${
                  view === v
                    ? "bg-secondary text-foreground"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {v === "onboard" ? "Onboard" : "Vault"}
              </button>
            ))}
          </div>
        </div>
      </nav>

      {/* Content */}
      <main className="px-6 py-16">
        <AnimatePresence mode="wait">
          {view === "onboard" ? (
            <motion.div
              key="onboard"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              {/* Hero */}
              <div className="text-center mb-14 max-w-lg mx-auto">
                <motion.div
                  initial={{ scale: 0.9, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  transition={{ delay: 0.1 }}
                  className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary/10 text-primary text-xs font-medium mb-5"
                >
                  Creator Onboarding
                </motion.div>
                <h1 className="text-3xl sm:text-4xl font-bold text-foreground tracking-tight mb-3">
                  Get started in <span className="text-gradient">minutes</span>
                </h1>
                <p className="text-muted-foreground text-sm leading-relaxed">
                  Connect your account and we'll sync your entire vault automatically.
                </p>
              </div>

              <OnboardingForm
                onComplete={(data) => {
                  setCreatorName(data.name);
                  setCreatorId(data.creatorId);
                  setWebhookUrl(data.webhookUrl || '');
                  console.log('Creator onboarded:', data);
                  if (data.webhookUrl) {
                    console.log('Webhook URL:', data.webhookUrl);
                  }
                  setView("dashboard");
                }}
              />
            </motion.div>
          ) : (
            <motion.div
              key="dashboard"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              {creatorId ? (
                <OnboardingSuccess
                  creatorName={creatorName || "Creator"}
                  creatorId={creatorId}
                  webhookUrl={webhookUrl}
                />
              ) : (
                <VaultDashboard creatorName={creatorName || "Creator"} />
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </main>
    </div>
  );
};

export default Index;
