package cart.service;

import cart.model.Cart;

public interface CartCommand {
    Cart execute();
    Cart undo();
}