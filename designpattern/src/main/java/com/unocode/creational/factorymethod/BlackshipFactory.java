package com.unocode.creational.factorymethod;

public class BlackshipFactory extends DefaultShipFactory {
    @Override
    public Ship createShip() {
        return new Blackship();
    }
}
