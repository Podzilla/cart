package service;

import cart.model.Cart;
import cart.model.CartItem;
import cart.model.OrderRequest;
import cart.repository.CartRepository;
import cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CartService cartService;

    private Cart cart;
    private CartItem cartItem;
    private final String customerId = "cust123";
    private final String productId = "prod456";
    private final String cartId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        cart = new Cart(cartId, customerId, new ArrayList<>(), false);
        cartItem = new CartItem();
        cartItem.setProductId(productId);
        cartItem.setQuantity(1);

        // Set orderServiceUrl using reflection
        Field orderServiceUrlField = CartService.class.getDeclaredField("orderServiceUrl");
        orderServiceUrlField.setAccessible(true);
        orderServiceUrlField.set(cartService, "http://localhost:8080");
    }

    @Test
    void createCart_existingCart_returnsCart() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        Cart result = cartService.createCart(customerId);

        assertEquals(cart, result);
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void createCart_noExistingCart_createsAndSavesCart() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.createCart(customerId);

        assertEquals(customerId, result.getCustomerId());
        assertFalse(result.isArchived());
        assertTrue(result.getItems().isEmpty());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addItemToCart_newItem_addsItem() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.addItemToCart(customerId, cartItem);

        assertEquals(1, result.getItems().size());
        assertEquals(cartItem, result.getItems().get(0));
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void addItemToCart_existingItem_updatesQuantity() {
        cart.getItems().add(cartItem);
        CartItem newItem = new CartItem();
        newItem.setProductId(productId);
        newItem.setQuantity(2);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.addItemToCart(customerId, newItem);

        assertEquals(1, result.getItems().size());
        assertEquals(3, result.getItems().get(0).getQuantity());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void addItemToCart_cartNotFound_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.addItemToCart(customerId, cartItem));
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItemQuantity_existingItem_updatesQuantity() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.updateItemQuantity(customerId, productId, 5);

        assertEquals(5, result.getItems().get(0).getQuantity());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void updateItemQuantity_quantityZero_removesItem() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.updateItemQuantity(customerId, productId, 0);

        assertTrue(result.getItems().isEmpty());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void updateItemQuantity_itemNotFound_throwsResponseStatusException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> cartService.updateItemQuantity(customerId, productId, 5));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItemFromCart_itemExists_removesItem() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.removeItemFromCart(customerId, productId);

        assertTrue(result.getItems().isEmpty());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void removeItemFromCart_cartNotFound_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.removeItemFromCart(customerId, productId));
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void deleteCartByCustomerId_cartExists_deletesCart() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        cartService.deleteCartByCustomerId(customerId);

        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).delete(cart);
    }

    @Test
    void deleteCartByCustomerId_cartNotFound_doesNothing() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        cartService.deleteCartByCustomerId(customerId);

        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).delete(any());
    }

    @Test
    void getCartByCustomerId_cartExists_returnsCart() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        Cart result = cartService.getCartByCustomerId(customerId);

        assertEquals(cart, result);
        verify(cartRepository).findByCustomerId(customerId);
    }

    @Test
    void getCartByCustomerId_cartNotFound_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.getCartByCustomerId(customerId));
        verify(cartRepository).findByCustomerId(customerId);
    }

    @Test
    void clearCart_cartExists_clearsItems() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.clearCart(customerId);

        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository).save(cart);
    }

    @Test
    void clearCart_cartNotFound_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.clearCart(customerId));
        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void archiveCart_activeCart_archivesCart() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.archiveCart(customerId);

        assertTrue(result.isArchived());
        verify(cartRepository).findByCustomerIdAndArchived(customerId, false);
        verify(cartRepository).save(cart);
    }

    @Test
    void archiveCart_noActiveCart_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.archiveCart(customerId));
        verify(cartRepository).findByCustomerIdAndArchived(customerId, false);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void unarchiveCart_archivedCart_unarchivesCart() {
        cart.setArchived(true);
        when(cartRepository.findByCustomerIdAndArchived(customerId, true)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.unarchiveCart(customerId);

        assertFalse(result.isArchived());
        verify(cartRepository).findByCustomerIdAndArchived(customerId, true);
        verify(cartRepository).save(cart);
    }

    @Test
    void unarchiveCart_noArchivedCart_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, true)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.unarchiveCart(customerId));
        verify(cartRepository).findByCustomerIdAndArchived(customerId, true);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void checkoutCart_validCart_sendsToOrderServiceAndClearsCart() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        when(restTemplate.postForObject(eq("http://localhost:8080/orders"), any(OrderRequest.class), eq(Void.class)))
                .thenReturn(null);

        Cart result = cartService.checkoutCart(customerId);

        assertTrue(result.getItems().isEmpty());
        verify(cartRepository).findByCustomerIdAndArchived(customerId, false);
        verify(restTemplate).postForObject(eq("http://localhost:8080/orders"), any(OrderRequest.class), eq(Void.class));
        verify(cartRepository).save(cart);
    }

    @Test
    void checkoutCart_noActiveCart_throwsNoSuchElementException() {
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> cartService.checkoutCart(customerId));
        verify(cartRepository).findByCustomerIdAndArchived(customerId, false);
        verify(restTemplate, never()).postForObject(any(), any(), any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void checkoutCart_orderServiceFails_throwsRuntimeException() {
        cart.getItems().add(cartItem);
        when(cartRepository.findByCustomerIdAndArchived(customerId, false)).thenReturn(Optional.of(cart));
        when(restTemplate.postForObject(eq("http://localhost:8080/orders"), any(OrderRequest.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Order Service error"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.checkoutCart(customerId));
        assertEquals("Error communicating with Order Service", exception.getMessage());
        verify(cartRepository).findByCustomerIdAndArchived(customerId, false);
        verify(restTemplate).postForObject(eq("http://localhost:8080/orders"), any(OrderRequest.class), eq(Void.class));
        verify(cartRepository, never()).save(any());
    }
}
