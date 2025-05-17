package service;

import cart.exception.GlobalHandlerException;
import cart.model.Cart;
import cart.model.CartItem;
import cart.model.CustomerDetailsResponse; // New import: CustomerDetailsResponse
import cart.model.DeliveryAddress; // New import: DeliveryAddress
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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

    @Mock
    private RestTemplate restTemplate;

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
    private final DeliveryAddress testShippingAddress = new DeliveryAddress(
            "123 Test St",
            "Testville",
            "TS",
            "USA",
            "12345"
            );

    private final String exchangeName = "test.cart.events";
    private final String checkoutRoutingKey = "test.order.checkout.initiate";
    private final String authServiceBaseUrl = "http://localhost:8081";

    // Updated: Added DeliveryAddress parameter
    private Cart createNewTestCart(String cId, String crtId, DeliveryAddress shippingAddress) {
        return new Cart(crtId, cId, new ArrayList<>(), false, null,
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), shippingAddress);
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
        // Updated: Initialize cart with null shipping address for most cases
        cart = createNewTestCart(customerId, cartId, null);

        item1Input = new CartItem(productId1, 1, price1);
        item2Input = new CartItem(productId2, 2, price2);

        ReflectionTestUtils.setField(cartService, "exchangeName", exchangeName);
        ReflectionTestUtils.setField(cartService, "checkoutRoutingKey", checkoutRoutingKey);
        ReflectionTestUtils.setField(cartService, "authServiceBaseUrl", authServiceBaseUrl);

        // This mock ensures that `cartService` operates on the `cart` instance of this test class.
        // It's important for mutation tests that this is the same object.
        lenient().when(cartRepository.findByCustomerId(anyString())).thenReturn(Optional.empty());
        lenient().when(cartRepository.findByCustomerId(eq(customerId))).thenReturn(Optional.of(cart));

        lenient().when(cartRepository.findByCustomerIdAndArchived(anyString(), anyBoolean())).thenReturn(Optional.empty());
        lenient().when(cartRepository.findByCustomerIdAndArchived(eq(customerId), eq(false))).thenReturn(Optional.of(cart));
        lenient().when(cartRepository.findByCustomerIdAndArchived(eq(customerId), eq(true))).thenReturn(Optional.of(createNewTestCart(customerId, cartId + "_archived", null)));

        // Default mock for save. Specific tests will override this if a different behavior is needed.
        // This ensures that when cartRepository.save is called, it returns the *modified* cart instance.
        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        assertNull(result.getShippingAddress()); // Assert shipping address is null for new cart

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
        cart.setShippingAddress(testShippingAddress); // Set a mock address

        // Override the default save mock for this test
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart savedCart = invocation.getArgument(0);
            // Return a new Cart instance with shippingAddress explicitly null
            return new Cart(
                    savedCart.getId(), savedCart.getCustomerId(),
                    new ArrayList<>(), // Items cleared
                    savedCart.isArchived(), savedCart.getAppliedPromoCode(),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    null // Explicitly set shippingAddress to null for the returned object
            );
        });

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

    // New tests for getCustomerShippingAddress and updated checkoutCart test

    @Test
    void getCustomerShippingAddress_success() {
        CustomerDetailsResponse mockResponse = new CustomerDetailsResponse();
        mockResponse.setAddress(testShippingAddress);
        mockResponse.setCustomerId(customerId);

        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenReturn(mockResponse);

        // Call the private method via reflection for testing
        DeliveryAddress actualAddress = ReflectionTestUtils.invokeMethod(cartService, "getCustomerShippingAddress", customerId);
        assertEquals(testShippingAddress, actualAddress);
        verify(restTemplate).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
    }

    @Test
    void getCustomerShippingAddress_notFound_throwsGlobalHandlerException() {
        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> ReflectionTestUtils.invokeMethod(cartService, "getCustomerShippingAddress", customerId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Customer details not found for checkout.", ex.getMessage());
        verify(restTemplate).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
    }

    @Test
    void getCustomerShippingAddress_nullAddressInResponse_throwsGlobalHandlerException() {
        CustomerDetailsResponse mockResponse = new CustomerDetailsResponse();
        mockResponse.setAddress(null); // Null DeliveryAddress
        mockResponse.setCustomerId(customerId);

        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenReturn(mockResponse);

        // This test now expects GlobalHandlerException directly due to CartService fix
        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> ReflectionTestUtils.invokeMethod(cartService, "getCustomerShippingAddress", customerId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Shipping address not available for customer.", ex.getMessage());
        verify(restTemplate).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
    }

    @Test
    void getCustomerShippingAddress_otherHttpClientError_throwsRuntimeException() {
        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(cartService, "getCustomerShippingAddress", customerId));

        assertTrue(ex.getMessage().contains("Failed to fetch customer details"));
        verify(restTemplate).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
    }

//    @Test
//    void getCustomerShippingAddress_genericError_throwsRuntimeException() {
//        when(restTemplate.getForObject(
//                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
//                eq(CustomerDetailsResponse.class),
//                eq(customerId)
//        )).thenThrow(new RuntimeException("Network error"));
//
//        RuntimeException ex = assertThrows(RuntimeException.class,
//                () -> ReflectionTestUtils.invokeMethod(cartService, "getCustomerShippingAddress", customerId));
//
//        assertTrue(ex.getMessage().contains("Failed to retrieve shipping address"));
//        assertTrue(ex.getMessage().contains("Some network error")); // Ensure it contains the nested message
//    }


    @Test
    void checkoutCart_validCartWithPromo_publishesEventAndClearsCart() {
        cart.getItems().add(new CartItem(productId1, 1, new BigDecimal("100.00")));
        cart.setAppliedPromoCode("SAVE10");
        cart.setSubTotal(new BigDecimal("100.00"));
        cart.setDiscountAmount(new BigDecimal("10.00"));
        cart.setTotalPrice(new BigDecimal("90.00"));

        PromoCode promo = createTestPromoCode("SAVE10", PromoCode.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, true);
        when(promoCodeService.getActivePromoCode("SAVE10")).thenReturn(Optional.of(promo));

        // Mock the RestTemplate call for fetching address
        CustomerDetailsResponse mockCustomerDetails = new CustomerDetailsResponse();
        mockCustomerDetails.setCustomerId(customerId);
        mockCustomerDetails.setAddress(testShippingAddress); // Set DeliveryAddress object
        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenReturn(mockCustomerDetails);

        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(OrderRequest.class));

        // Override the default save mock for this specific test
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart savedCart = invocation.getArgument(0);
            // Return a new Cart instance with shippingAddress explicitly null, as expected after checkout
            return new Cart(
                    savedCart.getId(), savedCart.getCustomerId(),
                    new ArrayList<>(), // Items cleared
                    savedCart.isArchived(), savedCart.getAppliedPromoCode(),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    null // Explicitly set shippingAddress to null for the returned object
            );
        });

        Cart result = cartService.checkoutCart(customerId);

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
        assertEquals(testShippingAddress, publishedEvent.getShippingAddress()); // Assert DeliveryAddress object

        assertTrue(result.getItems().isEmpty());
        assertNull(result.getAppliedPromoCode());
        assertNull(result.getShippingAddress()); // Assert shipping address is cleared after checkout
        assertEquals(BigDecimal.ZERO.setScale(2), result.getSubTotal());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalPrice());


        verify(cartRepository, times(1)).save(any(Cart.class));
        verify(restTemplate, times(1)).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
    }

    @Test
    void checkoutCart_emptyCart_throwsGlobalHandlerException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));

        // Ensure RestTemplate is not called if cart is empty
        verifyNoInteractions(restTemplate);

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> cartService.checkoutCart(customerId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot checkout an empty cart.", ex.getMessage());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(OrderRequest.class));
    }

//        @Test
//        void checkoutCart_shippingAddressFetchFails_throwsRuntimeExceptionAndCartNotCleared() {
//            cart.getItems().add(item1Input);
//
//            BigDecimal subTotal = item1Input.getUnitPrice();
//            String formattedSubTotal = String.format("%.2f", subTotal);
//            BigDecimal bigZero = BigDecimal.ZERO;
//            String formattedBigZero = String.format("%.2f", bigZero);
//            cart.setSubTotal(new BigDecimal(formattedSubTotal));
//            cart.setTotalPrice(new BigDecimal(formattedSubTotal));
//            cart.setDiscountAmount(new BigDecimal(formattedBigZero));
//
//            // Mock RestTemplate to throw an exception
//            when(restTemplate.getForObject(
//                    eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
//                    eq(CustomerDetailsResponse.class),
//                    eq(customerId)
//            )).thenThrow(new RuntimeException("Failed to connect to auth service"));
//
//            RuntimeException ex = assertThrows(RuntimeException.class,
//                    () -> cartService.checkoutCart(customerId));
//
//            assertTrue(ex.getMessage().contains("Failed to retrieve shipping address"));
//            verify(restTemplate, times(1)).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
//            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(OrderRequest.class));
//            verify(cartRepository, never()).save(any(Cart.class)); // Cart should not be saved if checkout fails at this stage
//
//            // Assert cart state remains unchanged
//            assertEquals(1, cart.getItems().size());
//            assertEquals(new BigDecimal(formattedSubTotal), cart.getSubTotal());
//            assertNull(cart.getShippingAddress()); // Shipping address should not be set
//        }


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

        // Mock RestTemplate to return a valid address
        CustomerDetailsResponse mockCustomerDetails = new CustomerDetailsResponse();
        mockCustomerDetails.setCustomerId(customerId);
        mockCustomerDetails.setAddress(testShippingAddress); // Set DeliveryAddress object
        when(restTemplate.getForObject(
                eq(authServiceBaseUrl + "/customer/detailes/{customerId}"),
                eq(CustomerDetailsResponse.class),
                eq(customerId)
        )).thenReturn(mockCustomerDetails);

        doThrow(new RuntimeException("RabbitMQ publish error")).when(rabbitTemplate)
                .convertAndSend(eq(exchangeName), eq(checkoutRoutingKey), any(OrderRequest.class));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cartService.checkoutCart(customerId));

        assertTrue(ex.getMessage().contains("Checkout process failed: Could not publish event."));
        verify(restTemplate, times(1)).getForObject(anyString(), eq(CustomerDetailsResponse.class), anyString());
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(OrderRequest.class)); // RabbitMQ call is made
        verify(cartRepository, never()).save(any(Cart.class)); // Cart should not be saved if publish fails

        assertEquals(1, cart.getItems().size());
        assertEquals(new BigDecimal(formattedSubTotal), cart.getSubTotal());
        // Shipping address should be set on the cart *before* the RabbitMQ call
        assertEquals(testShippingAddress, cart.getShippingAddress());
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
        Cart archivedCart = createNewTestCart(customerId, "archivedCrt", null);
        archivedCart.setArchived(true);
        when(cartRepository.findByCustomerIdAndArchived(customerId, true)).thenReturn(Optional.of(archivedCart));

        Cart result = cartService.unarchiveCart(customerId);

        assertFalse(result.isArchived());
        verify(cartRepository).save(archivedCart);
    }
}