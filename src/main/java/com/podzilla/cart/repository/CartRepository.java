package com.podzilla.cart.repository;

import com.podzilla.cart.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByCustomerId(String customerId);
    Optional<Cart> findByCustomerIdAndArchived(
            String customerId, boolean archived);

}
