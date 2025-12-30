package com.unocode.structural.composite;


public class Client {

    /*
    컴포짓 패턴
    컴포넌트가 없었으면 pricePrint 시 Item 용, Bag 용으로 구분이되어야 한다.
    하지만, 컴포넌트라는 인터페이스를 생성하고, 이를 각각 구현함으로서 간단하게 클라이언트가 사용할 수 있게 됨.
    추가적으로 Bag에서는 해당 클래스 자체가 컴포넌트를 리스트로 속성으로 갖게됨으로써 트리 구조로 price 접근이 가능하게 함.

    => **“객체들을 트리 구조로 구성해서, 단일 객체와 복합 객체를 동일하게 다루기 위한 구조 패턴”**

    “부분과 전체를 같은 인터페이스로 다룬다”
        단일 객체 (Leaf)
        복합 객체 (Composite)

     */

    public static void main(String[] args) {
        Item doranBlade = new Item("도란검", 450);
        Item healPotion = new Item("체력 물약", 50);

        Bag bag = new Bag();
        bag.add(doranBlade);
        bag.add(healPotion);

        Client client = new Client();
        client.printPrice(doranBlade);
        client.printPrice(bag);
    }

    private void printPrice(Component component) {
        System.out.println(component.getPrice());
    }


}
