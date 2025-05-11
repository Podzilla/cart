package cart.service;

import cart.model.Cart;
import cart.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class RemoveItemCommand implements CartCommand {

    private final CartService cartService;
    private final String customerId;
    private final String productId;
    private CartItem removedItem;

    @Override
    public Cart execute() {
        log.debug("Executing RemoveItemCommand for customerId: {}, productId: {}", customerId, productId);
        Cart cart = cartService.getCartByCustomerId(customerId);

        Optional<CartItem> itemToRemove = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (itemToRemove.isPresent()) {
            removedItem = new CartItem(itemToRemove.get().getProductId(), itemToRemove.get().getQuantity());
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
            log.debug("Item removed for productId: {}", productId);
        } else {
            log.warn("Item not found for removal, productId: {}", productId);
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("RemoveItemCommand executed, updated cart: {}", updatedCart);
        return updatedCart;
    }

    @Override
    public Cart undo() {
        log.debug("Undoing RemoveItemCommand for customerId: {}, productId: {}", customerId, productId);
        Cart cart = cartService.getCartByCustomerId(customerId);

        if (removedItem != null) {
            log.debug("Restoring item during undo for productId: {}", productId);
            cart.getItems().add(removedItem);
        } else {
            log.warn("No item to restore during undo for productId: {}", productId);
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("RemoveItemCommand undone, updated cart: {}", updatedCart);
        return updatedCart;
    }
}