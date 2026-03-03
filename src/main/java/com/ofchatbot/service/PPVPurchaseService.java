package com.ofchatbot.service;

import com.ofchatbot.entity.FanPurchase;
import com.ofchatbot.entity.PPVOffer;
import com.ofchatbot.repository.FanPurchaseRepository;
import com.ofchatbot.repository.PPVOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PPVPurchaseService {
    
    private final PPVOfferRepository ppvOfferRepository;
    private final FanPurchaseRepository fanPurchaseRepository;
    private final ShowerScriptService showerScriptService;
    
    public void recordPPVPurchase(Long fanId, Double purchaseAmount) {
        recordPPVPurchase(fanId, purchaseAmount, null, null);
    }

    /**
     * Record PPV purchase and advance script level if the purchased offer was a Shower script offer.
     * When Shower L6 is purchased, sends L7 after-care and marks script completed (never run twice).
     */
    public void recordPPVPurchase(Long fanId, Double purchaseAmount, String chatId, com.ofchatbot.entity.Creator creator) {
        List<PPVOffer> unpurchasedOffers = ppvOfferRepository.findByFanIdAndPurchasedFalse(fanId);
        
        for (PPVOffer offer : unpurchasedOffers) {
            if (Math.abs(offer.getPrice() - purchaseAmount) < 0.01) {
                offer.setPurchased(true);
                offer.setPurchasedAt(LocalDateTime.now());
                ppvOfferRepository.save(offer);
                
                String[] mediaIds = offer.getMediaIds().split(",");
                for (String mediaId : mediaIds) {
                    FanPurchase purchase = new FanPurchase();
                    purchase.setFanId(fanId);
                    purchase.setMediaId(mediaId.trim());
                    purchase.setLevel(offer.getLevel());
                    purchase.setPricePaid(purchaseAmount);
                    fanPurchaseRepository.save(purchase);
                }
                
                log.info("Recorded PPV purchase for fan {}: Level {}, ${}", 
                    fanId, offer.getLevel(), purchaseAmount);

                if (chatId != null && creator != null && ShowerScriptService.SCRIPT_ID_SHOWER.equals(offer.getScriptId()) && offer.getScriptLevel() != null) {
                    showerScriptService.onShowerPurchase(fanId, offer.getScriptLevel(), chatId, creator);
                }
                return;
            }
        }
        
        log.warn("No matching PPV offer found for fan {} with amount ${}", fanId, purchaseAmount);
    }

    /** Count PPV unlocks by this fan in the last N hours (for "session" momentum pricing). */
    public long countPurchasesInLastHours(Long fanId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return ppvOfferRepository.countPurchasesByFanIdSince(fanId, since);
    }
}
