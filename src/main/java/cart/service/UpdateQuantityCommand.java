package cart.service;

import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class UpdateQuantityCommand implements CartCommand {

    private final CartService cartService;
    private final String customerId;
    private final String productId;
    private final int newQuantity;
    private Integer previousQuantity;

    @Override
    public Cart execute() {
        log.debug("Executing UpdateQuantityCommand "
                + "for customerId: {}, productId: {}, quantity: "
                + "{}", customerId, productId, newQuantity);
        Cart cart = cartService.getCartByCustomerId(customerId);

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existingItemOpt.isEmpty()) {
            log.error("Product not found in cart for "
                    + "productId: {}", productId);
            throw new GlobalHandlerException(
                    HttpStatus.NOT_FOUND, "Product not found in cart");
        }

        CartItem item = existingItemOpt.get();
        previousQuantity = item.getQuantity();

        if (newQuantity <= 0) {
            log.debug("Removing item as quantity <= 0 for"
                    + " productId: {}", productId);
            cart.getItems().remove(item);
        } else {
            log.debug("Updating quantity to: {} for "
                    + "productId: {}", newQuantity, productId);
            item.setQuantity(newQuantity);
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("UpdateQuantityCommand executed, updated cart: {}", updatedCart);
        return updatedCart;
    }

    @Override
    public Cart undo() {
        log.debug("Undoing UpdateQuantityCommand for"
                + " customerId: {}, productId: {}", customerId, productId);
        Cart cart = cartService.getCartByCustomerId(customerId);

        if (previousQuantity == null) {
            log.warn("No previous quantity to restore for productId: {}", productId);
            return cart;
        }

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (previousQuantity <= 0) {
            log.debug("Restoring removed item during "
                    + "undo for productId: {}", productId);
            cart.getItems().add(new CartItem(productId, previousQuantity));
        } else if (existingItemOpt.isPresent()) {
            log.debug("Restoring previous quantity "
                    + "during undo for productId: {}", productId);
            existingItemOpt.get().setQuantity(previousQuantity);
        } else {
            log.debug("Adding item back during"
                    + " undo for productId: {}", productId);
            cart.getItems().add(new CartItem(productId, previousQuantity));
        }

        Cart updatedCart = cartService.saveCart(cart);
        log.debug("UpdateQuantityCommand undone,"
                + " updated cart: {}", updatedCart);
        return updatedCart;
    }
}
