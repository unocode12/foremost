package com.unocode.behavioral.command;


public class Client {

    /*
    커맨드패턴 : 요청을 캡슐화하여 호출자와 수신자를 분리하는 패턴.
    요청을 처리하는 방법이 바뀌더라도, 호출자의 코드는 바뀌지 않는다.
    “요청(행위)을 객체로 캡슐화해서, 요청하는 쪽과 실행하는 쪽을 분리하는 패턴”

    ***메서드를 직접 호출하지 않고, “호출을 객체로 만들어 던지면” → 커맨드 패턴***
     */

    private Command command;

    public Client(Command command) {
        this.command = command;
    }

    public void press() {
        command.execute();
    }

    public static void main(String[] args) {
        Client client = new Client(new GameStartCommand(new Game()));
        client.press();
    }
}
