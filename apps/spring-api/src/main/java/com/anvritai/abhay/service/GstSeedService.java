package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.gst.GstRate;
import com.anvritai.abhay.repository.gst.GstRateRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GstSeedService {
    private static final List<BigDecimal> STANDARD_RATES = List.of(
            new BigDecimal("0.00"), new BigDecimal("5.00"), new BigDecimal("12.00"),
            new BigDecimal("18.00"), new BigDecimal("28.00"));
    private final GstRateRepository rates;

    public GstSeedService(GstRateRepository rates) {
        this.rates = rates;
    }

    public void seedCompany(Company company) {
        for (BigDecimal rateValue : STANDARD_RATES) {
            if (rates.existsByCompanyIdAndRateAndCessRate(company.getId(), rateValue, BigDecimal.ZERO.setScale(2))) {
                continue;
            }
            GstRate rate = new GstRate();
            rate.setCompany(company);
            rate.setName("GST " + rateValue.stripTrailingZeros().toPlainString() + "%");
            rate.setRate(rateValue);
            rate.setCessRate(BigDecimal.ZERO.setScale(2));
            rate.setSystemRate(true);
            rate.setReverseChargeAllowed(true);
            rate.setActive(true);
            rates.save(rate);
        }
    }
}
