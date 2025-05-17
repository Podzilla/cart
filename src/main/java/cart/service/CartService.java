package cart.service;

import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import cart.model.ConfirmationType;
import cart.model.OrderRequest;
import cart.model.PromoCode;
import cart.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.podzilla.mq.EventPublisher;
import com.podzilla.mq.EventsConstants;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final EventPublisher eventPublisher;
    private final PromoCodeService promoCodeService;

    public CartService(final CartRepository cartRepository,
                      final EventPublisher eventPublisher,
                      final PromoCodeService promoCodeService) {
        this.cartRepository = cartRepository;
        this.eventPublisher = eventPublisher;
        this.promoCodeService = promoCodeService;
    }

    public Cart createCart(final String customerId) {
        log.debug("Entering createCart with customerId: {}", customerId);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(
                            UUID.randomUUID().toString(),
                            customerId,
                            new ArrayList<>(),
                            false,
                            null,
                            BigDecimal.ZERO.setScale(2),
                            BigDecimal.ZERO.setScale(2),
                            BigDecimal.ZERO.setScale(2)
                    );
                    log.debug("Cart created: {}", newCart);
                    return cartRepository.save(newCart);
                });
        log.debug("Cart retrieved: {}", cart);
        return cart;
    }

    public Cart addItemToCart(final String customerId, final CartItem newItem) {
        log.debug("Entering addItemToCart with customerId: {}, newItem: {}", customerId, newItem);
        CartCommand command = new AddItemCommand(this, customerId, newItem);
        return command.execute();
    }

    public Cart updateItemQuantity(final String customerId, final String productId, final int quantity) {
        log.debug("Entering updateItemQuantity with customerId:"
                + " {}, productId: {}, quantity: {}", customerId,
                productId, quantity);
        CartCommand command = new UpdateQuantityCommand(this, customerId, productId, quantity);
        return command.execute();
    }

    public Cart removeItemFromCart(final String customerId, final String productId) {
        log.debug("Entering removeItemFromCart with customerId: {}, productId: {}", customerId, productId);
        CartCommand command = new RemoveItemCommand(this, customerId, productId);
        return command.execute();
    }

    public void deleteCartByCustomerId(final String customerId) {
        log.debug("Entering deleteCartByCustomerId with customerId: {}", customerId);
        cartRepository.findByCustomerId(customerId)
                .ifPresent(cart -> {
                    log.debug("Deleting cart for customerId: {}", customerId);
                    cartRepository.delete(cart);
                });
        log.debug("Cart deletion completed for customerId: {}", customerId);
    }

    public Cart getCartByCustomerId(final String customerId) {
        log.debug("Entering getCartByCustomerId with customerId: {}", customerId);
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> {
                    log.error("Cart not found for customerId: {}", customerId);
                    throw new GlobalHandlerException(HttpStatus.NOT_FOUND, "Cart not found");
                });
        log.debug("Cart retrieved: {}", cart);
        return cart;
    }

    public void clearCart(final String customerId) {
        log.debug("Entering clearCart with customerId: {}", customerId);
        Cart cart = getCartByCustomerId(customerId);
        cart.getItems().clear();
        cart.setAppliedPromoCode(null);
        cart.setSubTotal(BigDecimal.ZERO.setScale(2));
        cart.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        cart.setTotalPrice(BigDecimal.ZERO.setScale(2));
        cartRepository.save(cart);
        log.debug("Cart cleared for customerId: {}", customerId);
    }

    public Cart archiveCart(final String customerId) {
        log.debug("Entering archiveCart with customerId: {}", customerId);
        Cart cart = getActiveCart(customerId);
        cart.setArchived(true);
        Cart archivedCart = cartRepository.save(cart);
        log.debug("Cart archived: {}", archivedCart);
        return archivedCart;
    }

    public Cart unarchiveCart(final String customerId) {
        log.debug("Entering unarchiveCart with customerId: {}", customerId);
        Cart cart = getArchivedCart(customerId);
        cart.setArchived(false);
        Cart activeCart = cartRepository.save(cart);
        log.debug("Cart unarchived: {}", activeCart);
        return activeCart;
    }

    private Cart getActiveCart(final String customerId) {
        log.debug("Entering getActiveCart with customerId: {}", customerId);
        Cart cart = cartRepository.findByCustomerIdAndArchived(customerId, false)
                .orElseThrow(() -> {
                    log.error("Active cart not found for customerId: {}", customerId);
                    return new NoSuchElementException("Cart not found for customer ID: " + customerId);
                });
        log.debug("Active cart retrieved: {}", cart);
        return cart;
    }

    private Cart getArchivedCart(final String customerId) {
        log.debug("Entering getArchivedCart with customerId: {}", customerId);
        Cart cart = cartRepository.findByCustomerIdAndArchived(customerId, true)
                .orElseThrow(() -> {
                    log.error("Archived cart not found for customerId: {}", customerId);
                    return new NoSuchElementException("No archived cart found for customer ID: " + customerId);
                });
        log.debug("Archived cart retrieved: {}", cart);
        return cart;
    }

    public Cart applyPromoCode(final String customerId, final String promoCodeInput) {
        log.debug("Entering applyPromoCode for customerId: {}, promoCode: {}", customerId, promoCodeInput);
        Cart cart = getActiveCart(customerId);
        String promoCodeUpper = promoCodeInput.toUpperCase();

        PromoCode promoCode = promoCodeService.getActivePromoCode(promoCodeUpper)
                .orElseThrow(() -> new GlobalHandlerException(
                        HttpStatus.BAD_REQUEST, "Invalid, inactive, or expired promo code: " + promoCodeInput));

        log.info("Applying valid promo code '{}' to cartId: {}", promoCodeUpper, cart.getId());
        cart.setAppliedPromoCode(promoCodeUpper);

        return saveCart(cart);
    }

    public Cart removePromoCode(final String customerId) {
        log.debug("Entering removePromoCode for customerId: {}", customerId);
        Cart cart = getActiveCart(customerId);

        if (cart.getAppliedPromoCode() != null) {
            log.info("Removing applied promo code '{}' from cartId: {}", cart.getAppliedPromoCode(), cart.getId());
            cart.setAppliedPromoCode(null);
            return saveCart(cart);
        } else {
            log.debug("No promo code to remove from cartId: {}", cart.getId());
            return cart;
        }
    }

    public Cart saveCart(final Cart cart) {
        log.debug("Preparing to save cartId: {}", cart.getId());
        recalculateCartTotals(cart);
        log.debug("Saving cart with updated totals: {}", cart);
        return cartRepository.save(cart);
    }

    private void recalculateCartTotals(final Cart cart) {
        log.debug("Recalculating totals for cartId: {}", cart.getId());

        BigDecimal subTotal = calculateSubTotal(cart);
        String formattedSubTotal = String.format("%.2f", subTotal);
        cart.setSubTotal(new BigDecimal(formattedSubTotal));

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (cart.getAppliedPromoCode() != null) {
            Optional<PromoCode> promoOpt = promoCodeService.getActivePromoCode(cart.getAppliedPromoCode());

            if (promoOpt.isPresent()) {
                PromoCode promo = promoOpt.get();
                boolean validForCart = true;

                if (promo.getExpiryDate() != null && promo.getExpiryDate().isBefore(Instant.now())) {
                    log.warn("Applied promo code {} is expired. Removing.", cart.getAppliedPromoCode());
                    cart.setAppliedPromoCode(null);
                    validForCart = false;
                }

                if (validForCart) {
                    if (promo.getDiscountType() == PromoCode.DiscountType.PERCENTAGE) {
                        BigDecimal percentageValue = promo.getDiscountValue().divide(new BigDecimal("100"));
                        String formattedPercentage = String.format("%.2f", percentageValue);
                        BigDecimal percentage = new BigDecimal(formattedPercentage);
                        discountAmount = subTotal.multiply(percentage);
                    } else if (promo.getDiscountType() == PromoCode.DiscountType.FIXED_AMOUNT) {
                        discountAmount = promo.getDiscountValue();
                    }
                }
            } else {
                log.warn("Applied promo code {} is no longer valid. Removing.", cart.getAppliedPromoCode());
                cart.setAppliedPromoCode(null);
            }
        }

        discountAmount = discountAmount.max(BigDecimal.ZERO);
        discountAmount = discountAmount.min(subTotal);
        String formattedDiscountAmount = String.format("%.2f", discountAmount);
        cart.setDiscountAmount(new BigDecimal(formattedDiscountAmount));

        BigDecimal totalPrice = subTotal.subtract(discountAmount);
        String formattedTotalPrice = String.format("%.2f", totalPrice);
        cart.setTotalPrice(new BigDecimal(formattedTotalPrice));

        log.debug("Recalculated totals for cartId: {}: SubTotal={}, Discount={}, Total={}",
                cart.getId(), cart.getSubTotal(), cart.getDiscountAmount(), cart.getTotalPrice());
    }

    private BigDecimal calculateSubTotal(final Cart cart) {
        if (cart.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return cart.getItems().stream()
                .filter(item -> item.getUnitPrice() != null && item.getQuantity() > 0)
                .map(CartItem::getItemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Cart checkoutCart(final String customerId, final ConfirmationType confirmationType, final String signature) {
        log.debug("Entering checkoutCart for customerId: {} with confirmationType: {}", 
                customerId, confirmationType);
        Cart cart = getActiveCart(customerId);

        recalculateCartTotals(cart);

        if (cart.getItems().isEmpty()) {
            log.warn("Attempted checkout for customerId: {} with an empty cart.", customerId);
            throw new GlobalHandlerException(HttpStatus.BAD_REQUEST, "Cannot checkout an empty cart.");
        }

        if (confirmationType == ConfirmationType.SIGNATURE && (signature == null || signature.trim().isEmpty())) {
            throw new GlobalHandlerException(HttpStatus.BAD_REQUEST, "Signature is required for SIGNATURE confirmation type");
        }

        OrderRequest checkoutEvent = new OrderRequest(
                UUID.randomUUID().toString(),
                customerId,
                cart.getId(),
                new ArrayList<>(cart.getItems()),
                cart.getSubTotal(),
                cart.getDiscountAmount(),
                cart.getTotalPrice(),
                cart.getAppliedPromoCode(),
                confirmationType,
                signature
        );

        try {
            log.debug("Publishing checkout event for cartId: {} with totals: Sub={}, Discount={}, Total={}, ConfirmationType={}",
                    cart.getId(), cart.getSubTotal(), cart.getDiscountAmount(), cart.getTotalPrice(), confirmationType);
            eventPublisher.publishEvent(EventsConstants.ORDER_PLACED, checkoutEvent);

            log.info("Checkout event published successfully for cartId: {}. Clearing cart.", cart.getId());
            cart.getItems().clear();
            cart.setAppliedPromoCode(null);
            Cart updatedCart = saveCart(cart);

            log.debug("Cart cleared and saved after checkout: {}", updatedCart);
            return updatedCart;

        } catch (Exception e) {
            log.error("Failed to publish checkout event for cartId: {}. Error: {}", cart.getId(), e.getMessage(), e);
            throw new RuntimeException("Checkout process failed: Could not publish event.", e);
        }
    }
}
