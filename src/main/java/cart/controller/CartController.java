package cart.controller;



import cart.model.Cart;
import cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import cart.model.CartItem;
import io.swagger.v3.oas.annotations.media.Content;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart Controller", description = "Handles cart operations like add, update, remove items and manage cart")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Create a new cart for a customer or return existing one")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart created or retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid customer ID provided", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PostMapping("/create/{customerId}")
    public ResponseEntity<Cart> createCart(@PathVariable("customerId") String customerId) {
        Cart cart = cartService.createCart(customerId);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Get cart by customer ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Cart not found for this customer")
    })
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Cart> getCartByCustomerId(@PathVariable("customerId") String customerId) {
        Cart cart = cartService.getCartByCustomerId(customerId);
        return ResponseEntity.ok(cart);
    }

    @Operation(summary = "Delete cart by customer ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Cart deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Cart not found")
    })
    @DeleteMapping("/customer/{customerId}")
    public ResponseEntity<Void> deleteCart(@PathVariable("customerId") String customerId) {
        cartService.deleteCartByCustomerId(customerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add an item to the cart or update its quantity if already exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item added or updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid item data provided"),
            @ApiResponse(responseCode = "404", description = "Cart not found for this customer")
    })
    @PostMapping("/{customerId}/items")
    public ResponseEntity<Cart> addItemToCart(
            @PathVariable("customerId") String customerId,
            @RequestBody CartItem cartItem) {
        Cart updatedCart = cartService.addItemToCart(customerId, cartItem);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Update quantity of an existing item in the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quantity updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid quantity value"),
            @ApiResponse(responseCode = "404", description = "Cart or item not found")
    })
    @PatchMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> updateItemQuantity(
            @PathVariable("customerId") String customerId,
            @PathVariable("productId") String productId,
            @RequestParam int quantity) {
        Cart updatedCart = cartService.updateItemQuantity(customerId, productId, quantity);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Remove an item from the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item removed successfully"),
            @ApiResponse(responseCode = "404", description = "Cart or item not found")
    })
    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> removeItemFromCart(
            @PathVariable("customerId") String customerId,
            @PathVariable("productId") String productId) {
        Cart updatedCart = cartService.removeItemFromCart(customerId, productId);
        return ResponseEntity.ok(updatedCart);
    }

    @Operation(summary = "Clear all items from the cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Cart cleared successfully"),
            @ApiResponse(responseCode = "404", description = "Cart not found")
    })
    @DeleteMapping("/{customerId}/clear")
    public ResponseEntity<Void> clearCart(@PathVariable("customerId") String customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive the cart (soft-delete)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart successfully archived"),
            @ApiResponse(responseCode = "404", description = "Cart not found")
    })
    @PatchMapping("/{customerId}/archive")
    public ResponseEntity<Cart> archiveCart(@PathVariable("customerId") String customerId) {
        Cart archivedCart = cartService.archiveCart(customerId);
        return ResponseEntity.ok(archivedCart);
    }

    @Operation(summary = "Unarchive a previously archived cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart successfully unarchived"),
            @ApiResponse(responseCode = "404", description = "Archived cart not found")
    })
    @PatchMapping("/{customerId}/unarchive")
    public ResponseEntity<Cart> unarchiveCart(@PathVariable("customerId") String customerId) {
        Cart activeCart = cartService.unarchiveCart(customerId);
        return ResponseEntity.ok(activeCart);
    }

    @Operation(summary = "Checkout cart by sending it to the Order Service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart checked out and sent to Order Service"),
            @ApiResponse(responseCode = "404", description = "Cart not found"),
            @ApiResponse(responseCode = "500", description = "Failed to communicate with Order Service")
    })
    @PostMapping("/{customerId}/checkout")
    public ResponseEntity<Cart> checkoutCart(@PathVariable("customerId") String customerId) {
        try {
            Cart updatedCart = cartService.checkoutCart(customerId);
            return ResponseEntity.ok(updatedCart);
        } catch (Exception ex) {
            throw new IllegalCallerException("Error communicating with Order Service");
        }
    }
}