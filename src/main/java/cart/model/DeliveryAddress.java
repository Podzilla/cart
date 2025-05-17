package cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class DeliveryAddress {
    private  String street;

    private  String city;

    private  String state;

    private  String country;

    private  String postalCode;

}
