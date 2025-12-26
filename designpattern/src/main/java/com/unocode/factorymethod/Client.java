package com.unocode.factorymethod;

public class Client {

    //"구체적으로 어떤 인스턴스를 만들지는 서브 클래스가 정한다."

    public static void main(String[] args) {
        Client client = new Client();
        client.print(new WhiteshipFactory(), "whiteship", "aa@mail.com");
        client.print(new BlackshipFactory(), "blackship", "aa@mail.com");
        ShipFactory factory = new WhiteshipFactory();
        Ship ship = factory.orderShip("White", "test@test.com");
    }
    // 팩토리 메서드 패턴은 객체 생성을 위한 인터페이스를 정의하고, 실제 생성할 객체의 결정은 서브클래스에 위임함으로써 객체 생성과 사용을 분리하는 생성 패턴이다.

    /*
    Creator (Factory)
     └─ factoryMethod()  ← 추상
         └─ ConcreteCreator
             └─ ConcreteProduct 생성*/
    /*
        ShipFactory (Factory) - createShip (FactoryMethod) - WhiteshipFactory (ConcreteCreator) - Whiteship (ConcreteProduct)
     */



    private void print(ShipFactory shipFactory, String name, String email) {
        System.out.println(shipFactory.orderShip(name, email));
    }

}
