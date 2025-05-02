package cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart {


    @Id
    private String id;

    private String customerId;

    private List<CartItem> items = new ArrayList<>();

    @Override
    public String toString() {
        return "Cart{"
                + "id='" + id + '\''
                + ", customerId='" + customerId + '\''
                + ", items=" + items
                + '}';
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final String customerId) {
        this.customerId = customerId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(final List<CartItem> items) {
        this.items = items;
    }
}

