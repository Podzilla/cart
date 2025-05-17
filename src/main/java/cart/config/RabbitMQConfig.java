package cart.config;

//import org.springframework.amqp.core.Binding;
//import org.springframework.amqp.core.BindingBuilder;
//import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key.checkout}")
    private String checkoutRoutingKey;

    // Define the exchange (e.g., a Topic Exchange)
    @Bean
    TopicExchange cartEventsExchange() {
        return new TopicExchange(exchangeName);
    }

    // Note: The Cart Service *produces* messages. The *consumer* (Order Service)
    // would typically define the Queue and the Binding. However, defining the
    // exchange here is good practice for the producer.
    // If the Cart service also needed to *consume* events (e.g., order confirmations),
    // you would define Queues and Bindings here as well.

    /* Example Consumer setup (would be in Order Service):
    @Value("${rabbitmq.queue.name.order}") // e.g., q.order.checkout
    private String orderQueueName;

    @Bean
    Queue orderQueue() {
        return new Queue(orderQueueName, true); // durable=true
    }

    @Bean
    Binding orderBinding(Queue orderQueue, TopicExchange cartEventsExchange) {
        return BindingBuilder.bind(orderQueue).to(cartEventsExchange).with(checkoutRoutingKey);
    }
    */

    // You might also need a MessageConverter bean (e.g., Jackson2JsonMessageConverter)
    // if you haven't configured one globally, to ensure your CheckoutEvent object
    // is serialized correctly (usually auto-configured by Spring Boot).
}
