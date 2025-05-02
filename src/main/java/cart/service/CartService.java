package cart.service;

import cart.model.Cart;
import cart.model.CartItem;
import cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;


    public Cart createCart(final String customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(UUID.randomUUID().toString(), customerId, new ArrayList<>());
                    return cartRepository.save(newCart);
                });
    }

    public Cart addItemToCart(final String customerId, final CartItem newItem) {
        Cart cart = getCartByCustomerId(customerId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(newItem.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + newItem.getQuantity());
        } else {
            cart.getItems().add(newItem);
        }

        return cartRepository.save(cart);
    }


    public Cart updateItemQuantity(final String customerId, final String productId, final int quantity) {
        Cart cart = getCartByCustomerId(customerId);

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existingItemOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found in cart");
        }

        CartItem item = existingItemOpt.get();

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }

        return cartRepository.save(cart);
    }


    public Cart removeItemFromCart(final String customerId,final String productId) {
        Cart cart = getCartByCustomerId(customerId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        return cartRepository.save(cart);
    }

    public void deleteCartByCustomerId(final String customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cartRepository::delete);
    }

    public Cart getCartByCustomerId(final String customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found"));
    }

    public void clearCart(final String customerId) {
        Cart cart = getCartByCustomerId(customerId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

}
