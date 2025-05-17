package cart.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import com.podzilla.mq.events.BaseEvent;

@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class OrderRequest extends BaseEvent {
    private String eventId;
    private String customerId;
    private String cartId;
    private List<CartItem> items;
    private BigDecimal subTotal;
    private BigDecimal discountAmount;
    private BigDecimal totalPrice;
    private String appliedPromoCode;
    private final ConfirmationType confirmationType;
    private String signature;

}
