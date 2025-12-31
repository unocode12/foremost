package com.unocode.structural.decorator;

public class Client {

    /*
    데코레이터 패턴 : 기존 코드를 변경하지 않고 부가 기능을 추가하는 패턴
    wrapping의 개념을 생각! 런타임에 동적으로 기능을 추가할 수 있다. 그러나 코드가 복잡해 질 수 있다.
     */

    private static boolean enabledSpamFilter = true;
    private static boolean enabledTrimming = true;

    private static class App {
        private final CommentService commentService;

        public App(CommentService commentService) {
            this.commentService = commentService;
        }

        public void writeComment(String comment) {
            commentService.addComment(comment);
        }
    }

    public static void main(String[] args) {
        CommentService commentService = new DefaultCommentService();

        if (enabledSpamFilter) {
            commentService = new SpamFilteringCommentDecorator(commentService);
        }

        if (enabledTrimming) {
            commentService = new TrimmingCommentDecorator(commentService);
        }

        App app = new App(commentService);
        app.writeComment("오징어게임");
        app.writeComment("보는게 하는거 보다 재밌을 수가 없지...");
        app.writeComment("http://hojin.me");
    }
}
