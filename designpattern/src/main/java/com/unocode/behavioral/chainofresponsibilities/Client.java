package com.unocode.behavioral.chainofresponsibilities;


public class Client {

    /*
    책임연쇄패턴 : 요청을 보내는 쪽과 처리하는 쪽을 분리하는 패턴, filtering 패턴

     */

    private RequestHandler requestHandler;

    public Client(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void doWork() {
        Request request = new Request("이번 놀이는 뽑기입니다.");
        requestHandler.handle(request);
    }

    public static void main(String[] args) {
        RequestHandler chain = new AuthRequestHandler(new LoggingRequestHandler(new PrintRequestHandler(null)));
        Client client = new Client(chain);
        client.doWork();
    }
}
