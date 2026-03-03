import { motion } from "framer-motion";
import { Check, ExternalLink, Copy } from "lucide-react";
import { useState } from "react";

interface OnboardingSuccessProps {
  creatorName: string;
  creatorId: string;
  webhookUrl?: string;
}

const OnboardingSuccess = ({ creatorName, creatorId, webhookUrl }: OnboardingSuccessProps) => {
  const [copied, setCopied] = useState(false);

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="max-w-md mx-auto text-center"
    >
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
        className="w-16 h-16 rounded-full bg-green-500/20 flex items-center justify-center mx-auto mb-6"
      >
        <Check className="w-8 h-8 text-green-500" />
      </motion.div>

      <h1 className="text-2xl font-bold text-foreground mb-2">
        Welcome, {creatorName}! 🎉
      </h1>
      
      <p className="text-muted-foreground mb-8">
        Your creator account has been successfully set up and is ready to go.
      </p>

      <div className="space-y-4 text-left">
        <div className="p-4 rounded-lg bg-secondary border border-border">
          <h3 className="font-medium text-foreground mb-2">Creator ID</h3>
          <div className="flex items-center gap-2">
            <code className="flex-1 text-sm font-mono text-muted-foreground bg-background px-2 py-1 rounded">
              {creatorId}
            </code>
            <button
              onClick={() => copyToClipboard(creatorId)}
              className="p-1.5 rounded hover:bg-background transition-colors"
              title="Copy Creator ID"
            >
              <Copy className="w-4 h-4 text-muted-foreground" />
            </button>
          </div>
        </div>

        {webhookUrl && (
          <div className="p-4 rounded-lg bg-secondary border border-border">
            <h3 className="font-medium text-foreground mb-2">Webhook URL</h3>
            <p className="text-sm text-muted-foreground mb-3">
              Configure this webhook URL in your OnlyFans settings to receive real-time messages.
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 text-xs font-mono text-muted-foreground bg-background px-2 py-1 rounded break-all">
                {webhookUrl}
              </code>
              <button
                onClick={() => copyToClipboard(webhookUrl)}
                className="p-1.5 rounded hover:bg-background transition-colors flex-shrink-0"
                title="Copy Webhook URL"
              >
                <Copy className="w-4 h-4 text-muted-foreground" />
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="mt-8 p-4 rounded-lg bg-blue-500/10 border border-blue-500/20">
        <h3 className="font-medium text-blue-600 mb-2">Next Steps</h3>
        <ul className="text-sm text-blue-600 space-y-1">
          <li>• Configure your OnlyFans webhook with the URL above</li>
          <li>• Test the connection by sending a message</li>
          <li>• Customize your AI response settings</li>
        </ul>
      </div>
    </motion.div>
  );
};

export default OnboardingSuccess;
