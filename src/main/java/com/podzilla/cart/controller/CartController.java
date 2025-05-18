package com.podzilla.cart.controller;

import com.podzilla.cart.model.Cart;
import com.podzilla.cart.service.CartService;
import com.podzilla.mq.events.ConfirmationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.podzilla.mq.events.DeliveryAddress;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import com.podzilla.cart.model.CartItem;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
@Tag(name = "Cart Controller", description = "Handles cart"
        + " operations like add, update,"
        + " remove items and manage cart")
@Slf4j
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Create a new cart for a "
            + "customer or return existing one")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Cart created or retrieved successfully"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid customer ID provided",
                    content = @Content),
            @ApiResponse(responseCode = "500",
                    description = "Internal server error",
                    content = @Content)
    })

    @PostMapping("/create")
    public ResponseEntity<Cart> createCart(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering createCart endpoint"
                + " with customerId:", customerId);
        Cart cart = cartService.createCart(customerId);
        log.debug("Cart created or retrieved:", cart);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Get cart by customer ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Cart retrieved successfully"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found for this customer")
    })

    @GetMapping("/customer")
    public ResponseEntity<Cart> getCartByCustomerId(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering getCartByCustomerId"
                + " endpoint with customerId:",
                customerId);
        Cart cart = cartService.getCartByCustomerId(customerId);
        log.debug("Cart retrieved:", cart);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Delete cart by customer ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Cart deleted successfully"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found")
    })

    @DeleteMapping("/customer")
    public ResponseEntity<Void> deleteCart(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering deleteCart end"
                + "point with customerId:", customerId);
        cartService.deleteCartByCustomerId(customerId);
        log.debug("Cart deleted for customerId:",
                customerId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add an item to the cart"
            + " or update its quantity if already exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Item added or updated successfully"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid item data provided"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found for this customer")
    })
    @PostMapping("/{customerId}/items")
    public ResponseEntity<Cart> addItemToCart(
            @RequestHeader("X-User-Id") final String customerId,
            @RequestBody final CartItem cartItem) {
        log.debug("Entering addItemToCart"
                + " endpoint with customerId: {},"
                + " cartItem: {}", customerId, cartItem);
        Cart updatedCart = cartService.addItemToCart(customerId, cartItem);
        log.debug("Cart updated with new item: {}", updatedCart);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Update quantity "
            + "of an existing item in the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Quantity updated successfully"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid quantity value"),
            @ApiResponse(responseCode = "404",
                    description = "Cart or item not found")
    })
    @PatchMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> updateItemQuantity(
            @RequestHeader("X-User-Id") final String customerId,
            @PathVariable("productId") final String productId,
            @RequestParam final int quantity) {
        log.debug("Entering updateItemQuantity"
                        + " endpoint with customerId:,"
                        + " productId: {}, quantity: {}",
                customerId, productId, quantity);
        Cart updatedCart = cartService.updateItemQuantity(
                customerId, productId, quantity);
        log.debug("Cart updated with new quantity:",
                updatedCart);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Remove an item from the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Item removed successfully"),
            @ApiResponse(responseCode = "404",
                    description = "Cart or item not found")
    })
    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> removeItemFromCart(
            @RequestHeader("X-User-Id") final String customerId,
            @PathVariable("productId") final String productId) {
        log.debug("Entering removeItemFromCart"
                + " endpoint with customerId:,"
                + " productId:", customerId, productId);
        Cart updatedCart = cartService
                .removeItemFromCart(customerId, productId);
        log.debug("Cart updated after item removal:",
                updatedCart);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Clear all items from the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Cart cleared successfully"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found")
    })

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering clearCart"
                + " endpoint with customerId:", customerId);
        cartService.clearCart(customerId);
        log.debug("Cart cleared for customerId:", customerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive the cart (soft-delete)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Cart successfully archived"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found")
    })

    @PatchMapping("/archive")
    public ResponseEntity<Cart> archiveCart(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering archiveCart"
                + " endpoint with customerId:", customerId);
        Cart archivedCart = cartService.archiveCart(customerId);
        log.debug("Cart archived:", archivedCart);
        return ResponseEntity.ok(archivedCart);
    }

    @Operation(summary = "Unarchive a previously archived cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Cart successfully unarchived"),
            @ApiResponse(responseCode = "404",
                    description = "Archived cart not found")
    })
    // Replace @PatchMapping("/{customerId}/unarchive")
    @PatchMapping("/unarchive")
    public ResponseEntity<Cart> unarchiveCart(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering unarchiveCart"
                + " endpoint with customerId:", customerId);
        Cart activeCart = cartService.unarchiveCart(customerId);
        log.debug("Cart unarchived:", activeCart);
        return ResponseEntity.ok(activeCart);
    }

    @Operation(summary = "Checkout cart by sending it to the Order Service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Cart checked out and sent to Order Service"),
            @ApiResponse(responseCode = "404",
                    description = "Cart not found"),
            @ApiResponse(responseCode = "500",
                    description = "Failed to communicate with Order Service")
    })

    @PostMapping("/checkout")
    public ResponseEntity<Cart> checkoutCart(
            @RequestHeader("X-User-Id") final String customerId,
            @RequestParam(required = true) final ConfirmationType confirmationType,
            @RequestParam(required = false) final String signature,
            @RequestParam(required = true) final Double longitude,
            @RequestParam(required = true) final Double latitude,
            @RequestParam(required = true) final DeliveryAddress address
            ) {
        log.debug("Entering checkoutCart endpoint with customerId: {},"
                        + " confirmationType: {}, signature: {}",
                customerId, confirmationType, signature);
        try {
            Cart updatedCart = cartService.checkoutCart(customerId, confirmationType,
             signature, longitude, latitude, address);
            log.debug("Cart checked out: {}", updatedCart);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception ex) {
            log.error("Error during checkout for customerId: {}", customerId, ex);
            throw new IllegalCallerException("Error communicating with Order Service");
        }
    }

    @Operation(summary = "Apply a promo code to the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Promo code applied successfully"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid or expired promo code"),
            @ApiResponse(responseCode = "404",
                    description = "Active cart not found")
    })

    @PostMapping("/promo/{promoCode}")
    public ResponseEntity<Cart> applyPromoCode(
            @RequestHeader("X-User-Id") final String customerId,
            @PathVariable("promoCode") final String promoCode) {
        log.debug("Entering applyPromoCode endpoint with "
                + "customerId: {}, promoCode: {}",
                customerId, promoCode);
        Cart updatedCart = cartService.applyPromoCode(
                customerId, promoCode);
        log.debug("Promo code applied, updated cart: "
                + "{}", updatedCart);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Remove the applied promo code from the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Promo code removed successfully"),
            @ApiResponse(responseCode = "404",
                    description = "Active cart not found")
    })

    @DeleteMapping("/promo")
    public ResponseEntity<Cart> removePromoCode(
            @RequestHeader("X-User-Id") final String customerId) {
        log.debug("Entering removePromoCode "
                + "endpoint with customerId: {}", customerId);
        Cart updatedCart = cartService.removePromoCode(customerId);
        log.debug("Promo code removed (if any),"
                + " updated cart: {}", updatedCart);
        return ResponseEntity.ok(updatedCart);
    }
}
