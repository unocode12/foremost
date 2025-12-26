package com.unocode.creational.factorymethod;

public class WhiteshipFactory extends DefaultShipFactory {

    @Override
    public Ship createShip() {
        return new Whiteship();
    }
}
