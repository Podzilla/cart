package cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "cart.repository")
public class CartApplication {
    public static void main(final String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
