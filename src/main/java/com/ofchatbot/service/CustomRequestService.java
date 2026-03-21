package com.ofchatbot.service;

import com.ofchatbot.entity.CustomRequest;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.repository.CustomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomRequestService {
    
    private final CustomRequestRepository customRequestRepository;
    private final OnlyFansApiService onlyFansApiService;
    
    /** $100/minute. 6 min minimum; 15+ min = flat $1,000. */
    private static final double PRICE_PER_MINUTE = 100.0;
    private static final int MIN_DURATION_MINUTES = 6;
    private static final int LONG_FORM_MINUTES = 15;
    private static final double LONG_FORM_PRICE = 1000.0;
    
    public CustomRequest processCustomRequest(Fan fan, String description, String requirements) {
        CustomRequest request = new CustomRequest();
        request.setFanId(fan.getId());
        request.setDescription(description);
        request.setRequirements(requirements);
        request.setStatus("pending");
        
        customRequestRepository.save(request);
        
        log.info("Created custom request {} for fan {}", request.getId(), fan.getId());
        
        return request;
    }
    
    public void quotePrice(CustomRequest request, int durationMinutes, Fan fan) {
        double quotedPrice = calculateCustomPrice(request, durationMinutes);
        
        request.setDurationMinutes(durationMinutes);
        request.setQuotedPrice(quotedPrice);
        request.setQuotedAt(LocalDateTime.now());
        request.setStatus("quoted");
        
        customRequestRepository.save(request);
        
        String quoteMessage = buildQuoteMessage(durationMinutes, quotedPrice);
        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), quoteMessage);
        
        log.info("Quoted custom request {}: {} minutes for ${}", 
            request.getId(), durationMinutes, quotedPrice);
    }
    
    /** Custom (named video): min $49.95. Always above standard; name = personalisation premium. */
    private static final double CUSTOM_MIN_PRICE = 49.95;

    /**
     * Calculate custom price: $100/min, 6 min minimum ($600), 15+ min = $1,000 flat.
     */
    public double calculateCustomPrice(CustomRequest request, int durationMinutes) {
        int effective = Math.max(MIN_DURATION_MINUTES, durationMinutes);
        if (effective >= LONG_FORM_MINUTES) {
            return LONG_FORM_PRICE;
        }
        return Math.max(CUSTOM_MIN_PRICE, effective * PRICE_PER_MINUTE);
    }

    /** Legacy: duration-only (no request). Uses same rules: 6 min min, 15+ = $1k. */
    public double calculateCustomPrice(int durationMinutes) {
        return calculateCustomPrice(null, durationMinutes);
    }
    
    private String buildQuoteMessage(int durationMinutes, double price) {
        // Short, natural, creator voice — no "chatting manager" or "our team" (Issue #10)
        return String.format(
            "custom vids are $%.0f for %d min 💕 i need half upfront to get started babe, send a tip when you're ready 😘",
            price, durationMinutes
        );
    }
    
    public void approveRequest(Long requestId) {
        CustomRequest request = customRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Custom request not found"));
        
        request.setStatus("approved");
        request.setApprovedAt(LocalDateTime.now());
        
        customRequestRepository.save(request);
        
        log.info("Approved custom request {}", requestId);
    }
    
    public void completeRequest(Long requestId) {
        CustomRequest request = customRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Custom request not found"));
        
        request.setStatus("completed");
        request.setCompletedAt(LocalDateTime.now());
        
        customRequestRepository.save(request);
        
        log.info("Completed custom request {}", requestId);
    }


    public boolean hasPendingCustomRequest(Long fanId) {
        return customRequestRepository.findByFanId(fanId).stream()
            .anyMatch(r -> "pending".equals(r.getStatus()) || "quoted".equals(r.getStatus()) || "advance_paid".equals(r.getStatus()));
    }

    /** Most recent custom request for fan that is pending, quoted, or advance_paid (for follow-up or quote). */
    public CustomRequest getPendingCustomRequest(Long fanId) {
        return customRequestRepository.findByFanId(fanId).stream()
            .filter(r -> "pending".equals(r.getStatus()) || "quoted".equals(r.getStatus()) || "advance_paid".equals(r.getStatus()))
            .max(Comparator.comparing(CustomRequest::getRequestedAt))
            .orElse(null);
    }

    /** Most recent quoted or advance_paid custom for fan (used when a tip arrives to apply 50% advance). */
    public CustomRequest getQuotedCustomRequestForFan(Long fanId) {
        return customRequestRepository.findByFanId(fanId).stream()
            .filter(r -> "quoted".equals(r.getStatus()) || "advance_paid".equals(r.getStatus()))
            .max(Comparator.comparing(r -> r.getQuotedAt() != null ? r.getQuotedAt() : r.getRequestedAt()))
            .orElse(null);
    }

    /** Parse duration from message (e.g. "5 min", "10 minutes"); default 6, clamp to 6–30. */
    public static int parseDurationFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return MIN_DURATION_MINUTES;
        }
        Matcher m = Pattern.compile("(\\d+)\\s*min(?:ute)?s?").matcher(message.trim().toLowerCase());
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            return Math.max(MIN_DURATION_MINUTES, Math.min(30, d));
        }
        return MIN_DURATION_MINUTES;
    }

    /**
     * If fan has a quoted custom and tip is at least 50% of quoted price, record advance and send "coming soon" message.
     * @return true if tip was applied as custom advance (then caller should not send generic tip thank you).
     */
    public boolean tryApplyTipToCustomAdvance(Fan fan, double tipAmount) {
        CustomRequest request = getQuotedCustomRequestForFan(fan.getId());
        if (request == null || !"quoted".equals(request.getStatus())) {
            return false;
        }
        Double quoted = request.getQuotedPrice();
        if (quoted == null || quoted <= 0) {
            return false;
        }
        double half = quoted * 0.5;
        if (tipAmount < half) {
            return false;
        }
        request.setAmountPaid(tipAmount);
        request.setStatus("advance_paid");
        customRequestRepository.save(request);
        String message = "got it babe, i'll have it ready for you soon 😘";
        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), message);
        log.info("Recorded 50% advance (${}) for custom request {} for fan {}", tipAmount, request.getId(), fan.getId());
        return true;
    }

    public CustomRequest getMostRecentCustomRequest(Long fanId) {
        return customRequestRepository.findByFanId(fanId).stream()
            .filter(r -> "approved".equals(r.getStatus()) || "completed".equals(r.getStatus()))
            .max((r1, r2) -> r1.getRequestedAt().compareTo(r2.getRequestedAt()))
            .orElse(null);
    }

    public String getCustomRequestSummary(CustomRequest request) {
        if (request == null) {
            return "No custom request details available";
        }

        return String.format("Custom request: %s (Duration: %d min, Price: $%.2f)",
            request.getDescription(),
            request.getDurationMinutes() != null ? request.getDurationMinutes() : 0,
            request.getQuotedPrice() != null ? request.getQuotedPrice() : 0.0
        );
    }

}
