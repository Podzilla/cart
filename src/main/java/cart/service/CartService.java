package cart.service;

import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import cart.model.OrderRequest;
import cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;

    private final RestTemplate restTemplate;

    @Value("${order.service.url}")
    private String orderServiceUrl;


    public Cart createCart(final String customerId) {
        log.debug("Entering createCart"
                + " with customerId:", customerId);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(UUID.randomUUID()
                            .toString(), customerId, new
                            ArrayList<>(), false);
                    log.debug("Cart created:", newCart);
                    return cartRepository.save(newCart);
                });
        log.debug("Cart retrieved:", cart);
        return cart;
    }

    public Cart addItemToCart(final String customerId,
                              final CartItem newItem) {
        log.debug("Entering addItemToCart "
                + "with customerId:, newItem:",
                customerId, newItem);
        CartCommand command = new AddItemCommand(this, customerId, newItem);
        return command.execute();
    }


    public Cart updateItemQuantity(final String customerId,
                                   final String productId, final int quantity) {
        log.debug("Entering updateItemQuantity with"
                        + " customerId:, productId:, quantity: ",
                customerId, productId, quantity);
        CartCommand command = new UpdateQuantityCommand(this, customerId, productId, quantity);
        return command.execute();
    }


    public Cart removeItemFromCart(final String customerId,
                                   final String productId) {
        log.debug("Entering removeItemFromCart"
                + " with customerId:, productId:", customerId, productId);

        CartCommand command = new RemoveItemCommand(this, customerId, productId);
        return command.execute();
    }

    public void deleteCartByCustomerId(final String customerId) {
        log.debug("Entering deleteCartByCustomerId"
                + " with customerId:", customerId);
        cartRepository.findByCustomerId(customerId)
                .ifPresent(cart -> {
                    log.debug("Deleting cart for customerId:", customerId);
                    cartRepository.delete(cart);
                });
        log.debug("Cart deletion completed for"
                + " customerId:", customerId);
    }

    public Cart getCartByCustomerId(final String customerId) {
        log.debug("Entering getCartByCustomerId"
                + " with customerId:", customerId);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> {
                    log.error("Cart not found for customerId:", customerId);
                    throw new GlobalHandlerException(
                            HttpStatus.NOT_FOUND, "Cart not found");
                });
        log.debug("Cart retrieved:", cart);
        return cart;

    }

    public void clearCart(final String customerId) {
        log.debug("Entering clearCart with customerId:", customerId);
        Cart cart = getCartByCustomerId(customerId);
        cart.getItems().clear();
        cartRepository.save(cart);
        log.debug("Cart cleared for customerId:", customerId);
    }

    public Cart archiveCart(final String customerId) {
        log.debug("Entering archiveCart with customerId:", customerId);
        Cart cart = getActiveCart(customerId);
        cart.setArchived(true);
        Cart archivedCart = cartRepository.save(cart);
        log.debug("Cart archived: {}", archivedCart);
        return archivedCart;
    }

    public Cart unarchiveCart(final String customerId) {
        log.debug("Entering unarchiveCart with customerId:", customerId);
        Cart cart = getArchivedCart(customerId);
        cart.setArchived(false);
        Cart activeCart = cartRepository.save(cart);
        log.debug("Cart unarchived:", activeCart);
        return activeCart;
    }

    public Cart checkoutCart(final String customerId) {
        log.debug("Entering checkoutCart with customerId:", customerId);
        Cart cart = getActiveCart(customerId);

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setCustomerId(customerId);
        orderRequest.setItems(cart.getItems());

        try {
            log.debug("Sending order request to"
                    + " Order Service for customerId:", customerId);
            restTemplate.postForObject(orderServiceUrl
                    + "/orders", orderRequest, Void.class);
            cart.getItems().clear();
            Cart updatedCart = cartRepository.save(cart);
            log.debug("Cart checked out and cleared:", updatedCart);
            return updatedCart;
        } catch (Exception e) {
            log.error("Failed to checkout cart for customerId:", customerId, e);
            throw new RuntimeException("Error"
                    + " communicating with Order Service", e);
        }
    }


    private Cart getActiveCart(final String customerId) {
        log.debug("Entering getActiveCart with customerId:", customerId);
        Cart cart = cartRepository.findByCustomerIdAndArchived(customerId,
                        false)
                .orElseThrow(() -> {
                    log.error("Active cart not found"
                            + " for customerId:", customerId);
                    return new NoSuchElementException("Cart not"
                            + " found for customer ID: " + customerId);
                });
        log.debug("Active cart retrieved:", cart);
        return cart;
    }

    private Cart getArchivedCart(final String customerId) {
        log.debug("Entering getArchivedCart with customerId:", customerId);
        Cart cart = cartRepository.findByCustomerIdAndArchived(customerId, true)
                .orElseThrow(() -> {
                    log.error("Archived cart not found"
                            + " for customerId:", customerId);
                    return new NoSuchElementException("No archived "
                            + "cart found for customer ID: " + customerId);
                });
        log.debug("Archived cart retrieved:", cart);
        return cart;
    }

    public Cart saveCart(final Cart cart) {
        return cartRepository.save(cart);
    }

}
