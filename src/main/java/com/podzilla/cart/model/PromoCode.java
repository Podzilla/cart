package com.podzilla.cart.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "promo_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {

    public enum DiscountType {
        PERCENTAGE,
        FIXED_AMOUNT
    }

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String code;

    private String description;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @Positive
    private BigDecimal discountValue;

    private boolean active = true;

    private Instant expiryDate;

    private BigDecimal minimumPurchaseAmount;

}
