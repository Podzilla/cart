package com.podzilla.cart.service;

import com.podzilla.cart.model.Cart;

public interface CartCommand {
    Cart execute();
    Cart undo();
}
