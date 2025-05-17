package service;
import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import cart.model.ConfirmationType;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private PromoCodeService promoCodeService;

    @InjectMocks
    private CartService cartService;

    private Cart cart;
    private CartItem item1Input;
    private CartItem item2Input;

    private final String customerId = "cust123";
    private final String productId1 = "prod1";
    private final String productId2 = "prod2";
    private final BigDecimal price1 = new BigDecimal("10.50");
    private final BigDecimal price2 = new BigDecimal("5.00");
    private final String cartId = UUID.randomUUID().toString();

    private final String exchangeName = "test.cart.events";
    private final String checkoutRoutingKey = "test.order.checkout.initiate";

    private Cart createNewTestCart(String cId, String crtId) {
        return new Cart(crtId, cId, new ArrayList<>(), false, null,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }

    private PromoCode createTestPromoCode(String code, PromoCode.DiscountType type, BigDecimal value, BigDecimal minPurchase, Instant expiry, boolean active) {
        PromoCode promo = new PromoCode();
        promo.setCode(code.toUpperCase());
        promo.setDiscountType(type);
        promo.setDiscountValue(value);
        promo.setMinimumPurchaseAmount(minPurchase);
        promo.setExpiryDate(expiry);
        promo.setActive(active);
        return promo;
    }

    @BeforeEach
    void setUp() {
        cart = createNewTestCart(customerId, cartId);

        item1Input = new CartItem(productId1, 1, price1);
        item2Input = new CartItem(productId2, 2, price2);

        ReflectionTestUtils.setField(cartService, "exchangeName", exchangeName);
        ReflectionTestUtils.setField(cartService, "checkoutRoutingKey", checkoutRoutingKey);

        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(cartRepository.findByCustomerId(anyString())).thenReturn(Optional.empty());
        lenient().when(cartRepository.findByCustomerId(eq(customerId))).thenReturn(Optional.of(cart));

        lenient().when(cartRepository.findByCustomerIdAndArchived(anyString(), anyBoolean())).thenReturn(Optional.empty());
        lenient().when(cartRepository.findByCustomerIdAndArchived(eq(customerId), eq(false))).thenReturn(Optional.of(cart));
        lenient().when(cartRepository.findByCustomerIdAndArchived(eq(customerId), eq(true))).thenReturn(Optional.of(createNewTestCart(customerId, cartId + "_archived")));
    }

    @Test
    void createCart_existingCart_returnsCartAndDoesNotSave() {
        Cart result = cartService.createCart(customerId);

        assertEquals(cart, result);
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void createCart_noExistingCart_createsAndSavesNewCartWithZeroTotals() {
        String newCustId = "newCust456";
        when(cartRepository.findByCustomerId(eq(newCustId))).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart newCart = invocation.getArgument(0);
            if (newCart.getId() == null) newCart.setId(UUID.randomUUID().toString());
            return newCart;
        });

        Cart result = cartService.createCart(newCustId);

        assertEquals(newCustId, result.getCustomerId());
        assertNotNull(result.getId());
        assertFalse(result.isArchived());
        assertTrue(result.getItems().isEmpty());
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        Cart savedCart = cartCaptor.getValue();
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getTotalPrice());
        assertNull(result.getAppliedPromoCode());

        verify(cartRepository).findByCustomerId(eq(newCustId));
    }

    @Test
    void addItemToCart_newItem_addsItemAndRecalculatesTotals() {
        Cart result = cartService.addItemToCart(customerId, item1Input);

        assertEquals(1, result.getItems().size());
        CartItem added = result.getItems().get(0);
        assertEquals(productId1, added.getProductId());
        assertEquals(1, added.getQuantity());
        assertEquals(price1, added.getUnitPrice());

        assertEquals(new BigDecimal("10.50").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("10.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void addItemToCart_existingItem_updatesQuantityAndRecalculatesTotals() {
        cart.getItems().add(new CartItem(productId1, 1, price1));

        CartItem additionalItem1 = new CartItem(productId1, 2, price1);
        Cart result = cartService.addItemToCart(customerId, additionalItem1);

        assertEquals(1, result.getItems().size());
        CartItem updatedItem = result.getItems().get(0);
        assertEquals(productId1, updatedItem.getProductId());
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("31.50").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("31.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void updateItemQuantity_existingItem_updatesAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1));

        Cart result = cartService.updateItemQuantity(customerId, productId1, 5);

        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("52.50").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("52.50").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void updateItemQuantity_quantityToZero_removesItemAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1));
        cart.getItems().add(new CartItem(productId2, 1, price2));

        Cart result = cartService.updateItemQuantity(customerId, productId1, 0);

        assertEquals(1, result.getItems().size());
        assertEquals(productId2, result.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void removeItemFromCart_itemExists_removesAndRecalculates() {
        cart.getItems().add(new CartItem(productId1, 2, price1));
        cart.getItems().add(new CartItem(productId2, 1, price2));

        Cart result = cartService.removeItemFromCart(customerId, productId1);

        assertEquals(1, result.getItems().size());
        assertEquals(productId2, result.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void clearCart_itemsExist_clearsItemsAndResetsTotalsAndPromo() {
        cart.getItems().add(new CartItem(productId1, 1, price1));
        cart.setAppliedPromoCode("TESTCODE");
        cart.setSubTotal(new BigDecimal("10.50"));
        cart.setDiscountAmount(new BigDecimal("1.00"));
        cart.setTotalPrice(new BigDecimal("9.50"));

        cartService.clearCart(customerId);

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        Cart savedCart = cartCaptor.getValue();
        assertTrue(savedCart.getItems().isEmpty());
        assertNull(savedCart.getAppliedPromoCode());
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), savedCart.getTotalPrice());
    }

    @Test
    void applyPromoCode_validPercentageCode_calculatesDiscount() {
        cart.getItems().add(new CartItem(productId1, 2, new BigDecimal("10.00")));

        String promoCodeStr = "SAVE10";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, true);
        when(promoCodeService.getActivePromoCode(promoCodeStr.toUpperCase())).thenReturn(Optional.of(promo));

        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        assertEquals(promoCodeStr.toUpperCase(), result.getAppliedPromoCode());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("2.00").setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("18.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void applyPromoCode_validFixedCode_calculatesDiscount() {
        cart.getItems().add(new CartItem(productId1, 3, new BigDecimal("10.00")));

        String promoCodeStr = "5OFF";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), null, null, true);
        when(promoCodeService.getActivePromoCode(promoCodeStr.toUpperCase())).thenReturn(Optional.of(promo));

        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        assertEquals(promoCodeStr.toUpperCase(), result.getAppliedPromoCode());
        assertEquals(new BigDecimal("30.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("5.00").setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("25.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void applyPromoCode_fixedDiscountExceedsSubtotal_discountCapped() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("3.00")));

        String promoCodeStr = "BIGOFF";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), null, null, true);
        when(promoCodeService.getActivePromoCode(promoCodeStr.toUpperCase())).thenReturn(Optional.of(promo));

        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        assertEquals(new BigDecimal("3.00").setScale(2), result.getSubTotal());
        assertEquals(new BigDecimal("3.00").setScale(2), result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalPrice());
    }

    @Test
    void applyPromoCode_invalidCode_throwsGlobalHandlerException() {
        String invalidCode = "FAKECODE";
        when(promoCodeService.getActivePromoCode(invalidCode.toUpperCase())).thenReturn(Optional.empty());

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.applyPromoCode(customerId, invalidCode));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid, inactive, or expired promo code"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void applyPromoCode_expiredCode_removesCodeAndNoDiscount() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));

        String promoCodeStr = "EXPIRED";
        PromoCode promo = createTestPromoCode(promoCodeStr, PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"),
                null, Instant.now().minusSeconds(3600), true);
        when(promoCodeService.getActivePromoCode(promoCodeStr.toUpperCase())).thenReturn(Optional.of(promo));

        Cart result = cartService.applyPromoCode(customerId, promoCodeStr);

        assertNull(result.getAppliedPromoCode());
        assertEquals(new BigDecimal("100.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("100.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void removePromoCode_codeExists_removesCodeAndResetsDiscount() {
        cart.getItems().add(new CartItem(productId1, 2, new BigDecimal("10.00")));
        cart.setAppliedPromoCode("SAVE10");
        cart.setSubTotal(new BigDecimal("20.00"));
        cart.setDiscountAmount(new BigDecimal("2.00"));
        cart.setTotalPrice(new BigDecimal("18.00"));

        Cart result = cartService.removePromoCode(customerId);

        assertNull(result.getAppliedPromoCode());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(new BigDecimal("20.00").setScale(2), result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void checkoutCart_validCartWithPromo_publishesEventAndClearsCart() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));
        cart.setAppliedPromoCode("SAVE10");
        cart.setSubTotal(new BigDecimal("100.00"));
        cart.setDiscountAmount(new BigDecimal("10.00"));
        cart.setTotalPrice(new BigDecimal("90.00"));

        PromoCode promo = createTestPromoCode("SAVE10", PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, true);
        when(promoCodeService.getActivePromoCode("SAVE10")).thenReturn(Optional.of(promo));

        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(OrderRequest.class));

        Cart result = cartService.checkoutCart(customerId, ConfirmationType.OTP, null);

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
        assertEquals(ConfirmationType.OTP, publishedEvent.getConfirmationType());

        assertTrue(result.getItems().isEmpty());
        assertNull(result.getAppliedPromoCode());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalPrice());

        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void checkoutCart_withSignature_publishesEventWithSignature() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));
        String signature = "customer_signature_data";

        Cart result = cartService.checkoutCart(customerId, ConfirmationType.SIGNATURE, signature);

        ArgumentCaptor<OrderRequest> eventCaptor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(rabbitTemplate).convertAndSend(eq(exchangeName), eq(checkoutRoutingKey), eventCaptor.capture());
        OrderRequest publishedEvent = eventCaptor.getValue();

        assertEquals(ConfirmationType.SIGNATURE, publishedEvent.getConfirmationType());
        assertEquals(signature, publishedEvent.getSignature());
    }

    @Test
    void checkoutCart_signatureTypeWithoutSignature_throwsException() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.checkoutCart(customerId, ConfirmationType.SIGNATURE, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Signature is required for SIGNATURE confirmation type", ex.getMessage());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(OrderRequest.class));
    }

    @Test
    void checkoutCart_emptyCart_throwsGlobalHandlerException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.checkoutCart(customerId, ConfirmationType.OTP, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot checkout an empty cart.", ex.getMessage());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(OrderRequest.class));
    }

    @Test
    void checkoutCart_rabbitMqFails_throwsRuntimeExceptionAndCartNotCleared() {
        cart.getItems().add(item1Input);

        BigDecimal subTotal = item1Input.getUnitPrice();
        String formattedSubTotal = String.format("%.2f", subTotal);
        BigDecimal bigZero = BigDecimal.ZERO;
        String formattedBigZero = String.format("%.2f", bigZero);
        cart.setSubTotal(new BigDecimal(formattedSubTotal));
        cart.setTotalPrice(new BigDecimal(formattedSubTotal));
        cart.setDiscountAmount(new BigDecimal(formattedBigZero));

        doThrow(new RuntimeException("RabbitMQ publish error")).when(rabbitTemplate)
                .convertAndSend(eq(exchangeName), eq(checkoutRoutingKey), any(OrderRequest.class));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cartService.checkoutCart(customerId, ConfirmationType.QR_CODE, null));

        assertTrue(ex.getMessage().contains("Checkout process failed: Could not publish event."));
        verify(cartRepository, never()).save(any(Cart.class));

        assertEquals(1, cart.getItems().size());
        assertEquals(new BigDecimal(formattedSubTotal), cart.getSubTotal());
    }

    @Test
    void getCartByCustomerId_cartExists_returnsCart() {
        Cart result = cartService.getCartByCustomerId(customerId);
        assertEquals(cart, result);
        verify(cartRepository).findByCustomerId(customerId);
    }

    @Test
    void getCartByCustomerId_cartNotFound_throwsGlobalHandlerException() {
        String nonExistentCustId = "ghost";
        when(cartRepository.findByCustomerId(eq(nonExistentCustId))).thenReturn(Optional.empty());

        GlobalHandlerException exception = assertThrows(GlobalHandlerException.class,
                () -> cartService.getCartByCustomerId(nonExistentCustId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Cart not found", exception.getMessage());
        verify(cartRepository).findByCustomerId(eq(nonExistentCustId));
    }

    @Test
    void archiveCart_activeCart_archivesCart() {
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
        Cart archivedCart = createNewTestCart(customerId, "archivedCrt");
        archivedCart.setArchived(true);
        when(cartRepository.findByCustomerIdAndArchived(customerId, true)).thenReturn(Optional.of(archivedCart));

        Cart result = cartService.unarchiveCart(customerId);

        assertFalse(result.isArchived());
        verify(cartRepository).save(archivedCart);
    }
}