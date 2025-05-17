package cart.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    @Field("_id")
    private String id;

    @NotBlank
    private String customerId;

    private List<CartItem> items = new ArrayList<>();

    private boolean archived = false;

    private String appliedPromoCode;

    private BigDecimal subTotal = BigDecimal.ZERO;

    private BigDecimal discountAmount = BigDecimal.ZERO;

    private BigDecimal totalPrice = BigDecimal.ZERO;

    private DeliveryAddress shippingAddress;

}

