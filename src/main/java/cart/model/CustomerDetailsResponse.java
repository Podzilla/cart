package cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class CustomerDetailsResponse {
    private String customerId;
    private String email;
    private String name;
    private String mobileNumber;
    private DeliveryAddress address;
}
