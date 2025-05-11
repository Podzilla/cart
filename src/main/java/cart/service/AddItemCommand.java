package cart.service;

import cart.model.Cart;
import cart.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class AddItemCommand implements CartCommand {

    private final CartService cartService;
    private final String customerId;
    private final CartItem newItem;

    @Override
    public Cart execute() {
        log.debug("Executing AddItemCommand for customerId: {}, item: {}", customerId, newItem);
        Cart cart = cartService.getCartByCustomerId(customerId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(newItem.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            log.debug("Item exists, updating quantity for productId: {}", newItem.getProductId());
            existingItem.get().setQuantity(existingItem.get().getQuantity() + newItem.getQuantity());
        } else {
            log.debug("Adding new item to cart for productId: {}", newItem.getProductId());
            cart.getItems().add(newItem);
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("AddItemCommand executed, updated cart: {}", updatedCart);
        return updatedCart;
    }

    @Override
    public Cart undo() {
        log.debug("Undoing AddItemCommand for customerId: {}, item: {}", customerId, newItem);
        Cart cart = cartService.getCartByCustomerId(customerId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(newItem.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            int newQuantity = existingItem.get().getQuantity() - newItem.getQuantity();
            if (newQuantity <= 0) {
                log.debug("Removing item during undo for productId: {}", newItem.getProductId());
                cart.getItems().removeIf(i -> i.getProductId().equals(newItem.getProductId()));
            } else {
                log.debug("Reducing quantity during undo for productId: {}", newItem.getProductId());
                existingItem.get().setQuantity(newQuantity);
            }
        } else {
            log.warn("Item not found during undo for productId: {}", newItem.getProductId());
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("AddItemCommand undone, updated cart: {}", updatedCart);
        return updatedCart;
    }
}
