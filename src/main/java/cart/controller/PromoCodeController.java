package cart.controller;

import cart.model.PromoCode;
import cart.service.PromoCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.List;

@RestController
@RequestMapping("/api/admin/promocodes")
@RequiredArgsConstructor
@Tag(name = "PromoCode Admin", description = "Manage promotional codes (Requires Admin Role)")
@Slf4j
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @Operation(summary = "Create a Promo Code")
    @PostMapping
    public ResponseEntity<PromoCode> createOrUpdatePromoCode(
            @Valid @RequestBody final PromoCode promoCode) {
        log.info("Admin request to create promo code: {}", promoCode.getCode());
        PromoCode savedPromoCode = promoCodeService.createOrUpdatePromoCode(promoCode);
        return ResponseEntity.ok(savedPromoCode);
    }

    @Operation(summary = "Get all Promo Codes")
    @GetMapping
    public ResponseEntity<List<PromoCode>> getAllPromoCodes() {
        log.info("Admin request to get all promo codes");
        return ResponseEntity.ok(promoCodeService.findAll());
    }

    @Operation(summary = "Get a specific Promo Code by code")
    @GetMapping("/{code}")
    public ResponseEntity<PromoCode> getPromoCodeByCode(
            @PathVariable final String code) {
        log.info("Admin request to get promo code: {}", code);
        return promoCodeService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a Promo Code by code")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deletePromoCode(
            @PathVariable final String code) {
        log.info("Admin request to delete promo code: {}", code);
        try {
            promoCodeService.deletePromoCode(code);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting promo code", code, e.getMessage());
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
