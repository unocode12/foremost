package com.unocode.creational.abstractfactory;

import com.unocode.creational.factorymethod.DefaultShipFactory;
import com.unocode.creational.factorymethod.Ship;
import com.unocode.creational.factorymethod.Whiteship;

public class WhiteshipFactory extends DefaultShipFactory {

    private ShipPartsFactory shipPartsFactory;

    public WhiteshipFactory(ShipPartsFactory shipPartsFactory) {
        this.shipPartsFactory = shipPartsFactory;
    }

    @Override
    public Ship createShip() {
        Ship ship = new Whiteship();
        ship.setAnchor(shipPartsFactory.createAnchor());
        ship.setWheel(shipPartsFactory.createWheel());
        return ship;
    }
}
