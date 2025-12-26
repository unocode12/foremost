package com.unocode.creational.factorymethod;

public interface ShipFactory {

    default Ship orderShip(String name, String email) {
        validate(name, email);
        prepareFor(name);
        Ship ship = createShip();
        sendEmailTo(email, ship);
        return ship;
    } // JAVA 8 default method
    
    static void printPolicy() {
        System.out.println("Ship factory policy");
    } // JAVA 8 static method

    void sendEmailTo(String email, Ship ship);

    Ship createShip();

    private void validate(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("배 이름을 지어주세요.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("연락처를 남겨주세요.");
        }
    } // JAVA 9 private method

    private void prepareFor(String name) {
        System.out.println(name + " 만들 준비 중");
    }

    private static void log(String msg) {
        System.out.println(msg);
    } // JAVA 9 private static method


}
