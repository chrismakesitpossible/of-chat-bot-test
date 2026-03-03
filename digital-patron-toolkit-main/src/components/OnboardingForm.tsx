import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ArrowRight, ArrowLeft, Check, User, Link, Key, Hash, Loader2 } from "lucide-react";
import { z } from "zod";
import { apiService, CreatorOnboardingRequest, CreatorOnboardingResponse } from "@/services/api";

const onboardSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100),
  onlyfansUrl: z.string().trim().url("Must be a valid URL").max(255),
  onlyfansApiKey: z.string().trim().min(1, "API key is required").max(255),
  onlyfansAccountId: z.string().trim().min(1, "Account ID is required").max(255),
});

type OnboardData = z.infer<typeof onboardSchema>;

const steps = [
  { id: "name", label: "Profile", icon: User, field: "name" as const, placeholder: "Jane Doe", description: "What's the creator's name?" },
  { id: "url", label: "OnlyFans URL", icon: Link, field: "onlyfansUrl" as const, placeholder: "https://onlyfans.com/janedoe", description: "Enter the OnlyFans profile URL" },
  { id: "apiKey", label: "API Key", icon: Key, field: "onlyfansApiKey" as const, placeholder: "ofapi_YOUR_KEY_HERE", description: "Paste the OnlyFans API key", sensitive: true },
  { id: "accountId", label: "Account ID", icon: Hash, field: "onlyfansAccountId" as const, placeholder: "acct_YOUR_ACCOUNT_ID", description: "Enter the account identifier" },
];

interface OnboardingFormProps {
  onComplete: (data: OnboardData & { creatorId: string; webhookUrl?: string }) => void;
}

const OnboardingForm = ({ onComplete }: OnboardingFormProps) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [formData, setFormData] = useState<Partial<OnboardData>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const step = steps[currentStep];
  const value = formData[step.field] || "";

  const validateCurrentField = () => {
    const fieldSchema = onboardSchema.shape[step.field];
    const result = fieldSchema.safeParse(value);
    if (!result.success) {
      setErrors({ [step.field]: result.error.errors[0].message });
      return false;
    }
    setErrors({});
    return true;
  };

  const handleNext = () => {
    if (!validateCurrentField()) return;
    if (currentStep < steps.length - 1) {
      setCurrentStep((s) => s + 1);
    } else {
      handleSubmit();
    }
  };

  const handleBack = () => {
    if (currentStep > 0) setCurrentStep((s) => s - 1);
  };

  const handleSubmit = async () => {
    const result = onboardSchema.safeParse(formData);
    if (!result.success) return;
    
    setIsSubmitting(true);
    try {
      // Ensure all required fields are present for the API call
      const requestData: CreatorOnboardingRequest = {
        name: result.data.name,
        onlyfansUrl: result.data.onlyfansUrl,
        onlyfansApiKey: result.data.onlyfansApiKey,
        onlyfansAccountId: result.data.onlyfansAccountId,
        tone: undefined,
        trackingCode: undefined
      };
      
      const response = await apiService.onboardCreator(requestData);
      
      if (response.success) {
        onComplete({
          ...result.data,
          creatorId: response.creatorId || '',
          webhookUrl: response.webhookUrl
        });
      } else {
        throw new Error(response.message);
      }
    } catch (error) {
      console.error('Onboarding failed:', error);
      setErrors({ submit: error instanceof Error ? error.message : 'Onboarding failed' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleNext();
  };

  const isLastStep = currentStep === steps.length - 1;
  const progress = ((currentStep + 1) / steps.length) * 100;

  return (
    <div className="w-full max-w-lg mx-auto">
      {/* Progress bar */}
      <div className="mb-10">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-medium text-muted-foreground tracking-wider uppercase">
            Step {currentStep + 1} of {steps.length}
          </span>
          <span className="text-xs font-mono text-muted-foreground">{Math.round(progress)}%</span>
        </div>
        <div className="h-1 w-full rounded-full bg-secondary overflow-hidden">
          <motion.div
            className="h-full rounded-full bg-primary"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.4, ease: "easeOut" }}
          />
        </div>
      </div>

      {/* Step indicators */}
      <div className="flex items-center gap-2 mb-10">
        {steps.map((s, i) => {
          const Icon = s.icon;
          const isActive = i === currentStep;
          const isDone = i < currentStep;
          return (
            <motion.div
              key={s.id}
              className={`flex items-center justify-center w-9 h-9 rounded-lg text-xs font-medium transition-all duration-300 ${
                isDone
                  ? "bg-primary/20 text-primary"
                  : isActive
                  ? "bg-primary text-primary-foreground glow-primary"
                  : "bg-secondary text-muted-foreground"
              }`}
              whileHover={{ scale: 1.05 }}
            >
              {isDone ? <Check className="w-4 h-4" /> : <Icon className="w-4 h-4" />}
            </motion.div>
          );
        })}
      </div>

      {/* Form field */}
      <AnimatePresence mode="wait">
        <motion.div
          key={step.id}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          transition={{ duration: 0.25 }}
        >
          <label className="block text-sm font-medium text-muted-foreground mb-2">
            {step.label}
          </label>
          <h2 className="text-2xl font-semibold text-foreground mb-6">{step.description}</h2>
          <input
            type={step.sensitive ? "password" : "text"}
            value={value}
            onChange={(e) => {
              setFormData({ ...formData, [step.field]: e.target.value });
              setErrors({});
            }}
            onKeyDown={handleKeyDown}
            placeholder={step.placeholder}
            autoFocus
            className="w-full px-4 py-3.5 rounded-xl bg-secondary border border-border text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary/50 transition-all font-mono text-sm"
          />
          {errors[step.field] && (
            <motion.p
              initial={{ opacity: 0, y: -5 }}
              animate={{ opacity: 1, y: 0 }}
              className="text-destructive text-sm mt-2"
            >
              {errors[step.field]}
            </motion.p>
          )}
          {errors.submit && (
            <motion.p
              initial={{ opacity: 0, y: -5 }}
              animate={{ opacity: 1, y: 0 }}
              className="text-destructive text-sm mt-2"
            >
              {errors.submit}
            </motion.p>
          )}
        </motion.div>
      </AnimatePresence>

      {/* Actions */}
      <div className="flex items-center justify-between mt-10">
        <button
          onClick={handleBack}
          disabled={currentStep === 0}
          className="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={handleNext}
          disabled={isSubmitting}
          className="flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:opacity-90 glow-primary transition-all disabled:opacity-50"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Onboarding...
            </>
          ) : isLastStep ? (
            <>
              Complete
              <Check className="w-4 h-4" />
            </>
          ) : (
            <>
              Continue
              <ArrowRight className="w-4 h-4" />
            </>
          )}
        </button>
      </div>
    </div>
  );
};

export default OnboardingForm;
