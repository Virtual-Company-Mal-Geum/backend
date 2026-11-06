package com.malgeum.geo.domain.pricing.account.dto;

import java.time.YearMonth;
import java.util.List;

import com.malgeum.geo.domain.pricing.account.entity.PricingMode;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription.PartnerTier;

public record PricingProfileResponse(PricingMode mode, PartnerTier partnerTier,
                                     List<PricingMode> availableImprovementSources, ScanAllowance scanAllowance) {
    public record ScanAllowance(boolean unlimited, YearMonth month, Integer monthlyLimit,
                                int freeScanUsedCount, Integer remaining) {
    }

}
