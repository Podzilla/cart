//package api;
//
//import cart.model.Cart;
//import cart.model.CartItem;
//import cart.repository.CartRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.web.client.ExpectedCount;
//import org.springframework.test.web.client.MockRestServiceServer;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
//import static org.hamcrest.Matchers.*;
//import static org.junit.jupiter.api.Assertions.*;
//import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
//import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
//import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
//import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//@RequiredArgsConstructor
//@ActiveProfiles("test")
//public class CartControllerTests {
//
//    private MockMvc mockMvc;
//
//    private CartRepository cartRepository;
//
//    private ObjectMapper objectMapper;
//
//    private RestTemplate restTemplate;
//
//    private MockRestServiceServer mockServer;
//
//    @Value("${order.service.url}")
//    private String orderServiceUrl;
//
//    private String customerId;
//    private String productId1;
//    private String productId2;
//
//    @BeforeEach
//    void setUp() {
//        // Initialize MockRestServiceServer
//        mockServer = MockRestServiceServer.createServer(restTemplate);
//
//        cartRepository.deleteAll(); // Clean slate before each test
//
//        customerId = "cust-" + UUID.randomUUID().toString();
//        productId1 = "prod-" + UUID.randomUUID().toString();
//        productId2 = "prod-" + UUID.randomUUID().toString();
//    }
//
//    @AfterEach
//    void tearDown() {
//        cartRepository.deleteAll(); // Clean up after each test
//        mockServer.verify(); // Verify all expected RestTemplate calls were made
//    }
//
//    private Cart createAndSaveTestCart(String custId, boolean archived, CartItem... items) {
//        Cart cart = new Cart(UUID.randomUUID().toString(), custId, new ArrayList<>(List.of(items)), archived);
//        return cartRepository.save(cart);
//    }
//
//    @Test
//    void createCart_shouldCreateNewCart_whenCartDoesNotExist() throws Exception {
//        mockMvc.perform(post("/api/carts/create/{customerId}", customerId))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.customerId", is(customerId)))
//                .andExpect(jsonPath("$.items", empty()))
//                .andExpect(jsonPath("$.archived", is(false)));
//
//        assertTrue(cartRepository.findByCustomerId(customerId).isPresent());
//    }
//
//    @Test
//    void createCart_shouldReturnExistingCart_whenCartExists() throws Exception {
//        Cart existingCart = createAndSaveTestCart(customerId, false, new CartItem(productId1, 1));
//
//        mockMvc.perform(post("/api/carts/create/{customerId}", customerId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id", is(existingCart.getId())))
//                .andExpect(jsonPath("$.customerId", is(customerId)))
//                .andExpect(jsonPath("$.items[0].productId", is(productId1)));
//
//        assertEquals(1, cartRepository.count());
//    }
//
//    @Test
//    void getCartByCustomerId_shouldReturnCart_whenExists() throws Exception {
//        Cart cart = createAndSaveTestCart(customerId, false, new CartItem(productId1, 2));
//
//        mockMvc.perform(get("/api/carts/customer/{customerId}", customerId))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.id", is(cart.getId())))
//                .andExpect(jsonPath("$.items[0].productId", is(productId1)))
//                .andExpect(jsonPath("$.items[0].quantity", is(2)));
//    }
//
//    @Test
//    void getCartByCustomerId_shouldReturnNotFound_whenNotExists() throws Exception {
//        mockMvc.perform(get("/api/carts/customer/{customerId}", "non-existent-customer"))
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    void deleteCart_shouldDeleteCartAndReturnNoContent() throws Exception {
//        createAndSaveTestCart(customerId, false);
//
//        mockMvc.perform(delete("/api/carts/customer/{customerId}", customerId))
//                .andExpect(status().isNoContent());
//
//        assertFalse(cartRepository.findByCustomerId(customerId).isPresent());
//    }
//
//    @Test
//    void deleteCart_shouldDoNothingAndReturnNoContent_whenCartNotFound() throws Exception {
//        mockMvc.perform(delete("/api/carts/customer/{customerId}", customerId))
//                .andExpect(status().isNoContent()); // Service method handles not found gracefully for delete
//    }
//
//
//    @Test
//    void addItemToCart_shouldAddNewItemToExistingCart() throws Exception {
//        createAndSaveTestCart(customerId, false);
//        CartItem newItem = new CartItem(productId1, 3);
//
//        mockMvc.perform(post("/api/carts/{customerId}/items", customerId)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(newItem)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items", hasSize(1)))
//                .andExpect(jsonPath("$.items[0].productId", is(productId1)))
//                .andExpect(jsonPath("$.items[0].quantity", is(3)));
//
//        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertEquals(1, updatedCart.getItems().size());
//        assertEquals(productId1, updatedCart.getItems().get(0).getProductId());
//    }
//
//    @Test
//    void addItemToCart_shouldUpdateQuantityIfItemExists() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 2));
//        CartItem itemToAdd = new CartItem(productId1, 3); // Adding more of the same item
//
//        mockMvc.perform(post("/api/carts/{customerId}/items", customerId)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(itemToAdd)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items", hasSize(1)))
//                .andExpect(jsonPath("$.items[0].productId", is(productId1)))
//                .andExpect(jsonPath("$.items[0].quantity", is(5))); // 2 + 3
//
//        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertEquals(5, updatedCart.getItems().get(0).getQuantity());
//    }
//
//    @Test
//    void updateItemQuantity_shouldUpdateQuantityOfExistingItem() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 2));
//        int newQuantity = 5;
//
//        mockMvc.perform(patch("/api/carts/{customerId}/items/{productId}", customerId, productId1)
//                        .param("quantity", String.valueOf(newQuantity)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items[0].quantity", is(newQuantity)));
//
//        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertEquals(newQuantity, updatedCart.getItems().get(0).getQuantity());
//    }
//
//    @Test
//    void updateItemQuantity_shouldRemoveItemIfQuantityIsZero() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 2));
//
//        mockMvc.perform(patch("/api/carts/{customerId}/items/{productId}", customerId, productId1)
//                        .param("quantity", "0"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items", empty()));
//
//        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertTrue(updatedCart.getItems().isEmpty());
//    }
//
//    @Test
//    void updateItemQuantity_shouldReturnNotFound_whenItemNotInCart() throws Exception {
//        createAndSaveTestCart(customerId, false); // Cart exists but is empty
//
//        mockMvc.perform(patch("/api/carts/{customerId}/items/{productId}", customerId, "non-existent-product")
//                        .param("quantity", "5"))
//                .andExpect(status().isNotFound()); // Based on CartService logic throwing GlobalHandlerException
//    }
//
//
//    @Test
//    void removeItemFromCart_shouldRemoveItem() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 1), new CartItem(productId2, 1));
//
//        mockMvc.perform(delete("/api/carts/{customerId}/items/{productId}", customerId, productId1))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items", hasSize(1)))
//                .andExpect(jsonPath("$.items[0].productId", is(productId2)));
//
//        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertEquals(1, updatedCart.getItems().size());
//        assertEquals(productId2, updatedCart.getItems().get(0).getProductId());
//    }
//
//    @Test
//    void clearCart_shouldRemoveAllItemsFromCart() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 1), new CartItem(productId2, 1));
//
//        mockMvc.perform(delete("/api/carts/{customerId}/clear", customerId))
//                .andExpect(status().isNoContent());
//
//        Cart clearedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertTrue(clearedCart.getItems().isEmpty());
//    }
//
//    @Test
//    void archiveCart_shouldSetArchivedToTrue() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 1));
//
//        mockMvc.perform(patch("/api/carts/{customerId}/archive", customerId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.archived", is(true)));
//
//        Cart archivedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertTrue(archivedCart.isArchived());
//    }
//
//    @Test
//    void archiveCart_shouldReturnNotFound_whenActiveCartNotExists() throws Exception {
//        // Ensure no active cart exists or only an archived one
//        createAndSaveTestCart(customerId, true); // Save an already archived cart
//
//        mockMvc.perform(patch("/api/carts/{customerId}/archive", customerId))
//                .andExpect(status().isNotFound()); // Because getActiveCart will throw NoSuchElementException
//    }
//
//    @Test
//    void unarchiveCart_shouldSetArchivedToFalse() throws Exception {
//        createAndSaveTestCart(customerId, true, new CartItem(productId1, 1)); // Start with an archived cart
//
//        mockMvc.perform(patch("/api/carts/{customerId}/unarchive", customerId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.archived", is(false)));
//
//        Cart unarchivedCart = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertFalse(unarchivedCart.isArchived());
//    }
//
//    @Test
//    void unarchiveCart_shouldReturnNotFound_whenArchivedCartNotExists() throws Exception {
//        // Ensure no archived cart exists or only an active one
//        createAndSaveTestCart(customerId, false); // Save an active cart
//
//        mockMvc.perform(patch("/api/carts/{customerId}/unarchive", customerId))
//                .andExpect(status().isNotFound()); // Because getArchivedCart will throw NoSuchElementException
//    }
//
//    @Test
//    void checkoutCart_shouldClearCartAndCallOrderService_whenSuccessful() throws Exception {
//        Cart cartToCheckout = createAndSaveTestCart(customerId, false, new CartItem(productId1, 2), new CartItem(productId2, 1));
//
//        // Expect a POST request to the order service
//        mockServer.expect(ExpectedCount.once(),
//                        requestTo(orderServiceUrl + "/orders"))
//                .andExpect(method(HttpMethod.POST))
//                // You can add more specific assertions for the request body if needed:
//                // .andExpect(content().json(objectMapper.writeValueAsString(expectedOrderRequest)))
//                .andRespond(withSuccess()); // Simulate a successful response from Order Service
//
//        mockMvc.perform(post("/api/carts/{customerId}/checkout", customerId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.customerId", is(customerId)))
//                .andExpect(jsonPath("$.items", empty())); // Cart items should be cleared
//
//        Cart finalCartState = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertTrue(finalCartState.getItems().isEmpty());
//        assertFalse(finalCartState.isArchived()); // Should remain active but empty
//    }
//
//    @Test
//    void checkoutCart_shouldReturnInternalServerError_whenOrderServiceFails() throws Exception {
//        createAndSaveTestCart(customerId, false, new CartItem(productId1, 1));
//
//        mockServer.expect(ExpectedCount.once(),
//                        requestTo(orderServiceUrl + "/orders"))
//                .andExpect(method(HttpMethod.POST))
//                .andRespond(withServerError()); // Simulate an error from Order Service
//
//        mockMvc.perform(post("/api/carts/{customerId}/checkout", customerId))
//                .andExpect(status().isInternalServerError()) // Based on your controller's exception handling
//                .andExpect(content().string(containsString("Error communicating with Order Service"))); // Check error message
//
//        // Cart should NOT be cleared if order service call fails
//        Cart cartStateAfterFailure = cartRepository.findByCustomerId(customerId).orElseThrow();
//        assertFalse(cartStateAfterFailure.getItems().isEmpty());
//    }
//
//    @Test
//    void checkoutCart_shouldReturnNotFound_whenCartNotExists() throws Exception {
//        mockMvc.perform(post("/api/carts/{customerId}/checkout", "non-existent-customer"))
//                .andExpect(status().isNotFound());
//    }
//}