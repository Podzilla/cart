package cart.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String eventId;
    private String customerId;
    private String cartId;
    private List<CartItem> items;
    private BigDecimal subTotal;
    private BigDecimal discountAmount;
    private BigDecimal totalPrice;
    private String appliedPromoCode;

}
