package cart.model;


import java.util.List;

public class OrderRequest {
    private String customerId;
    private List<CartItem> items;

    // Getters and setters
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
