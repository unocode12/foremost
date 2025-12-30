package com.unocode.structural.composite;

public class Character implements Component {

    private Bag bag;

    @Override
    public int getPrice() {
        return bag.getPrice();
    }

}
