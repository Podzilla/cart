package com.podzilla.cart.service;

import com.podzilla.cart.exception.GlobalHandlerException;
import com.podzilla.cart.model.PromoCode;
import com.podzilla.cart.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoCode createOrUpdatePromoCode(final PromoCode promoCode) {
        log.info("Creating/Updating promo code: {}", promoCode.getCode());
        promoCode.setCode(promoCode.getCode().toUpperCase());
        Optional<PromoCode> existing = promoCodeRepository.findByCode(promoCode.getCode());
        existing.ifPresent(value -> promoCode.setId(value.getId()));

        return promoCodeRepository.save(promoCode);
    }

    public Optional<PromoCode> findByCode(final String code) {
        return promoCodeRepository.findByCode(code.toUpperCase());
    }

    public List<PromoCode> findAll() {
        return promoCodeRepository.findAll();
    }

    public void deletePromoCode(final String code) {
        PromoCode promo = promoCodeRepository.findByCode(
                code.toUpperCase())
                .orElseThrow(() -> new
                        GlobalHandlerException(HttpStatus.NOT_FOUND,
                        "Promo code not found: " + code));
        promoCodeRepository.delete(promo);
        log.info("Deleted promo code: {}", code);
    }

    public Optional<PromoCode> getActivePromoCode(final String code) {
        return promoCodeRepository.findByCode(code.toUpperCase())
                .filter(PromoCode::isActive);
    }
}
