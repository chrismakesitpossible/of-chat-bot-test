package com.ofchatbot.service;

import com.ofchatbot.entity.CustomRequest;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.repository.CustomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomRequestService {
    
    private final CustomRequestRepository customRequestRepository;
    private final OnlyFansApiService onlyFansApiService;
    
    private static final double PREMIUM_CUSTOM_PRICE = 1000.0;
    private static final int PREMIUM_DURATION_MINUTES = 20;
    private static final double BUDGET_CUSTOM_PRICE = 600.0;
    private static final int BUDGET_DURATION_MINUTES = 6;
    private static final double PRICE_PER_MINUTE = 100.0;
    
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
        double quotedPrice = calculateCustomPrice(durationMinutes);
        
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

    public double calculateCustomPrice(int durationMinutes) {
        double price;
        if (durationMinutes >= PREMIUM_DURATION_MINUTES) {
            price = PREMIUM_CUSTOM_PRICE;
        } else if (durationMinutes >= 15) {
            price = 1000.0;
        } else if (durationMinutes >= BUDGET_DURATION_MINUTES) {
            price = BUDGET_CUSTOM_PRICE;
        } else {
            price = durationMinutes * PRICE_PER_MINUTE;
        }
        return Math.max(CUSTOM_MIN_PRICE, price);
    }
    
    private String buildQuoteMessage(int durationMinutes, double price) {
        if (durationMinutes >= PREMIUM_DURATION_MINUTES) {
            return String.format(
                "For a %d-minute custom video, it's $%.0f 💕\n\n" +
                "Just send the tip and I'll make something amazing for you 😘",
                durationMinutes, price
            );
        } else {
            return String.format(
                "For a %d-minute custom, it would be $%.0f 💕\n\n" +
                "Or if you want something longer and more special, I can do 20 minutes for $1000 😏\n\n" +
                "Just send the tip when you're ready babe 😘",
                durationMinutes, price
            );
        }
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
            .anyMatch(r -> "pending".equals(r.getStatus()) || "quoted".equals(r.getStatus()));
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
