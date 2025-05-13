package service;

import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import cart.model.OrderRequest;
import cart.model.PromoCode;
import cart.repository.CartRepository;

import cart.service.CartService;
import cart.service.PromoCodeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private PromoCodeService promoCodeService;

    @Spy
    @InjectMocks
    private CartService cartService;

    private Cart cart;
    private CartItem item1Input; // Item as input to service methods
    private CartItem item2Input;

    private final String customerId = "cust123";
    private final String productId1 = "prod1";
    private final String productId2 = "prod2";
    private final BigDecimal price1 = new BigDecimal("10.50");
    private final BigDecimal price2 = new BigDecimal("5.00");
    private final String cartId = UUID.randomUUID().toString();

    private final String exchangeName = "test.cart.events";
    private final String checkoutRoutingKey = "test.order.checkout.initiate";

    private Cart createTestCartInstance() {
        return new Cart(cartId, customerId, new ArrayList<>(), false, null,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }

    private PromoCode createTestPromoCode(String code, PromoCode.DiscountType type, BigDecimal value, BigDecimal minPurchase, Instant expiryDate, boolean isActive) {
        PromoCode promo = new PromoCode();
        promo.setCode(code.toUpperCase());
        promo.setActive(isActive);
        promo.setDiscountType(type);
        promo.setDiscountValue(value);
        promo.setMinimumPurchaseAmount(minPurchase);
        promo.setExpiryDate(expiryDate);
        return promo;
    }

    @BeforeEach
    void setUp() {
        cart = createTestCartInstance();
        item1Input = new CartItem(productId1, 1, price1);
        item2Input = new CartItem(productId2, 2, price2);

        ReflectionTestUtils.setField(cartService, "exchangeName", exchangeName);
        ReflectionTestUtils.setField(cartService, "checkoutRoutingKey", checkoutRoutingKey);


        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cartToSave = invocation.getArgument(0);

            return cartToSave;
        });



        lenient().when(cartService.getCartByCustomerId(customerId)).thenReturn(cart);
        lenient().when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));
        lenient().when(cartRepository.findByCustomerIdAndArchived(customerId, true))
                .thenReturn(Optional.of(new Cart(cartId, customerId, new ArrayList<>(), true, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

    }

    @Test
    void createCart_existingCart_returnsCartAndDoesNotSave() {
        Cart existingCart = createTestCartInstance();
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

        Cart result = cartService.createCart(customerId);

        assertEquals(existingCart, result);
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void createCart_noExistingCart_createsAndSavesNewCartWithZeroTotals() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart newCart = invocation.getArgument(0);
            newCart.setId(cartId);
            return newCart;
        });


        Cart result = cartService.createCart(customerId);

        assertEquals(customerId, result.getCustomerId());
        assertNotNull(result.getId());
        assertFalse(result.isArchived());
        assertTrue(result.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalPrice());
        assertNull(result.getAppliedPromoCode());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(any(Cart.class));
    }


    @Test
    void addItemToCart_newItem_addsItemAndRecalculatesTotals() {
        // Act
        Cart result = cartService.addItemToCart(customerId, item1Input); // 1 x 10.50

        // Assert
        assertEquals(1, result.getItems().size());
        assertTrue(result.getItems().stream().anyMatch(i -> i.getProductId().equals(productId1) && i.getQuantity() == 1));
        assertEquals(new BigDecimal("10.50").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("10.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class)); // saveCart is called by command
    }

    @Test
    void addItemToCart_existingItem_updatesQuantityAndRecalculatesTotals() {
        // Initial state: cart has item1
        cart.getItems().add(new CartItem(productId1, 1, price1));
        cartService.saveCart(cart); // Save to set initial totals

        // Act: Add more of item1
        CartItem additionalItem1 = new CartItem(productId1, 2, price1);
        Cart result = cartService.addItemToCart(customerId, additionalItem1); // Adds 2, total qty = 3

        // Assert
        assertEquals(1, result.getItems().size());
        CartItem updatedItem = result.getItems().get(0);
        assertEquals(productId1, updatedItem.getProductId());
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("31.50").setScale(2), result.getSubTotal()); // 10.50 * 3
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("31.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }


    @Test
    void updateItemQuantity_existingItem_updatesAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1)); // 2 * 10.50 = 21.00
        cartService.saveCart(cart); // Set initial totals

        // Act
        Cart result = cartService.updateItemQuantity(customerId, productId1, 5); // Update to 5 * 10.50 = 52.50

        // Assert
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("52.50").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("52.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }

    @Test
    void updateItemQuantity_quantityToZero_removesItemAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1));
        cart.getItems().add(new CartItem(productId2, 1, price2)); // p1: 21.00, p2: 5.00. Sub: 26.00
        cartService.saveCart(cart);

        // Act: Remove productId1
        Cart result = cartService.updateItemQuantity(customerId, productId1, 0);

        // Assert
        assertEquals(1, result.getItems().size());
        assertEquals(productId2, result.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }

    @Test
    void removeItemFromCart_itemExists_removesAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1));
        cart.getItems().add(new CartItem(productId2, 1, price2));
        cartService.saveCart(cart);

        // Act
        Cart result = cartService.removeItemFromCart(customerId, productId1);

        // Assert
        assertEquals(1, result.getItems().size());
        assertEquals(productId2, result.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }

    @Test
    void clearCart_itemsExist_clearsItemsAndResetsTotals() {
        cart.getItems().add(new CartItem(productId1, 1, price1));
        cart.setAppliedPromoCode("TESTCODE");
        cartService.saveCart(cart); // Initial save to set totals

        // Act
        cartService.clearCart(customerId); // This modifies 'cart' in place due to getCartByCustomerId

        // Assert
        // The cart object itself should be modified. saveCart is called inside clearCart.
        assertTrue(cart.getItems().isEmpty());
        assertNull(cart.getAppliedPromoCode());
        assertEquals(BigDecimal.ZERO.setScale(2), cart.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), cart.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), cart.getTotalPrice());
        verify(cartRepository, times(2)).save(cart); // Initial save + save in clearCart
    }

    // --- Promo Code Tests ---
    @Test
    void applyPromoCode_validPercentageCode_calculatesDiscount() {
        cart.getItems().add(new CartItem(productId1, 2, new BigDecimal("10.00"))); // SubTotal = 20.00
        cartService.saveCart(cart); // Initial calculation

        String promoCodeStr = "SAVE10";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, true);
        when(promoCodeService.getActivePromoCode(promoCodeStr)).thenReturn(Optional.of(promo));

        // Act
        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        // Assert
        assertEquals(promoCodeStr, result.getAppliedPromoCode());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("2.00").setScale(2), result.getDiscountAmount()); // 10% of 20.00
        assertEquals(new BigDecimal("18.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, atLeastOnce()).save(any(Cart.class));
    }

    @Test
    void applyPromoCode_validFixedCode_calculatesDiscount() {
        cart.getItems().add(new CartItem(productId1, 3, new BigDecimal("10.00"))); // SubTotal = 30.00
        cartService.saveCart(cart);

        String promoCodeStr = "5OFF";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), null, null, true);
        when(promoCodeService.getActivePromoCode(promoCodeStr)).thenReturn(Optional.of(promo));

        // Act
        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        // Assert
        assertEquals(promoCodeStr, result.getAppliedPromoCode());
        assertEquals(new BigDecimal("30.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("25.00").setScale(2), result.getTotalPrice());
    }

    @Test
    void applyPromoCode_invalidCode_throwsException() {
        String invalidCode = "FAKECODE";
        when(promoCodeService.getActivePromoCode(invalidCode.toUpperCase())).thenReturn(Optional.empty());

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.applyPromoCode(customerId, invalidCode));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid, inactive, or expired promo code"));
        assertNull(cart.getAppliedPromoCode()); // Should not be set
    }

    @Test
    void applyPromoCode_expiredCode_noDiscountCodeRemoved() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));
        cartService.saveCart(cart);

        String promoCodeStr = "EXPIRED";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, Instant.now().minusSeconds(3600), true); // Expired 1 hour ago
        when(promoCodeService.getActivePromoCode(promoCodeStr)).thenReturn(Optional.of(promo)); // Mock this to return it, recalc logic should handle expiry

        // Act
        Cart result = cartService.applyPromoCode(customerId, promoCodeStr); // applies then save calls recalculate

        // Assert
        // RecalculateTotals should detect expiry based on the logic added in CartService.
        // If it removes the code:
        assertNull(result.getAppliedPromoCode());
        assertEquals(new BigDecimal("100.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("100.00").setScale(2), result.getTotalPrice());
    }


    @Test
    void removePromoCode_codeExists_removesCodeAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, new BigDecimal("10.00"))); // SubTotal = 20.00
        cart.setAppliedPromoCode("SAVE10");
        // Manually set initial discount for testing removal logic
        cart.setSubTotal(new BigDecimal("20.00"));
        cart.setDiscountAmount(new BigDecimal("2.00"));
        cart.setTotalPrice(new BigDecimal("18.00"));
        // No need to call cartService.saveCart(cart); as we are testing removePromoCode directly after setting this state.

        // Act
        Cart result = cartService.removePromoCode(customerId);

        // Assert
        assertNull(result.getAppliedPromoCode());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getTotalPrice());
    }

    // --- Checkout Tests ---
    @Test
    void checkoutCart_validCartWithPromo_publishesEventWithCorrectTotalsAndClearsCart() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));
        cart.setAppliedPromoCode("SAVE10");
        // Simulate totals as if SAVE10 (10%) was applied correctly before checkout
        cart.setSubTotal(new BigDecimal("100.00"));
        cart.setDiscountAmount(new BigDecimal("10.00"));
        cart.setTotalPrice(new BigDecimal("90.00"));

        // Mock promoCodeService for the recalculateCartTotals call within checkoutCart
        PromoCode promo = createTestPromoCode("SAVE10", PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, true);
        when(promoCodeService.getActivePromoCode("SAVE10")).thenReturn(Optional.of(promo));
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(OrderRequest.class));


        // Act
        Cart result = cartService.checkoutCart(customerId);

        // Assert Published Event
        ArgumentCaptor<OrderRequest> eventCaptor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(rabbitTemplate).convertAndSend(eq(exchangeName), eq(checkoutRoutingKey), eventCaptor.capture());
        OrderRequest publishedEvent = eventCaptor.getValue();

        assertEquals(customerId, publishedEvent.getCustomerId());
        assertEquals(cartId, publishedEvent.getCartId());
        assertEquals(1, publishedEvent.getItems().size());
        assertEquals(new BigDecimal("100.00").setScale(2), publishedEvent.getSubTotal());
        assertEquals(new BigDecimal("10.00").setScale(2), publishedEvent.getDiscountAmount());
        assertEquals(new BigDecimal("90.00").setScale(2), publishedEvent.getTotalPrice());
        assertEquals("SAVE10", publishedEvent.getAppliedPromoCode());

        // Assert Cart State after Checkout (returned by method)
        assertTrue(result.getItems().isEmpty());
        assertNull(result.getAppliedPromoCode());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalPrice());

        verify(cartRepository, times(1)).save(any(Cart.class)); // Save for clearing
    }

    @Test
    void checkoutCart_emptyCart_throwsException() {
        // cart is empty by default in setup for this test
        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.checkoutCart(customerId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot checkout an empty cart.", ex.getMessage());
        verify(rabbitTemplate, never()).convertAndSend(eq(exchangeName), eq(checkoutRoutingKey), any(OrderRequest.class));
    }

    @Test
    void checkoutCart_rabbitMqFails_throwsRuntimeExceptionAndCartNotCleared() {
        cart.getItems().add(item1Input);
        cartService.saveCart(cart); // Initial state

        doThrow(new RuntimeException("RabbitMQ publish error")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(OrderRequest.class));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cartService.checkoutCart(customerId));
        assertTrue(ex.getMessage().contains("Checkout process failed: Could not publish event."));

        // Verify cart state is NOT cleared
        Cart cartAfterFailedCheckout = cartService.getCartByCustomerId(customerId); // Re-fetch
        assertEquals(1, cartAfterFailedCheckout.getItems().size());
        assertNotNull(cartAfterFailedCheckout.getSubTotal()); // Should still have its subtotal from before failure
    }


    // --- Old tests to adapt or verify ---
    @Test
    void getCartByCustomerId_cartNotFound_throwsGlobalHandlerException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        assertThrows(GlobalHandlerException.class, () -> cartService.getCartByCustomerId(customerId));
    }

    @Test
    void archiveCart_activeCart_archivesCart() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));

        Cart result = cartService.archiveCart(customerId);

        assertTrue(result.isArchived());
        verify(cartRepository).save(cart);
    }

    @Test
    void archiveCart_noActiveCart_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> cartService.archiveCart(customerId));
    }

    @Test
    void unarchiveCart_archivedCart_unarchivesCart() {
        cart.setArchived(true);
        when(cartRepository.findByCustomerIdAndArchived(customerId, true)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.empty());

        Cart result = cartService.unarchiveCart(customerId);

        assertFalse(result.isArchived());
        verify(cartRepository).save(cart);
    }
}
