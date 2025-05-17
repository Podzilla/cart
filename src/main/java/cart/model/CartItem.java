package cart.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @NotBlank
    private String productId;

    @NotNull
    @PositiveOrZero
    private int quantity;

    @NotNull
    @PositiveOrZero
    private BigDecimal unitPrice;

    public BigDecimal getItemTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
