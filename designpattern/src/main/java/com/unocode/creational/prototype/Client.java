package com.unocode.creational.prototype;

import java.util.ArrayList;
import java.util.List;

public class Client {

    public static void main(String[] args) throws CloneNotSupportedException {
        GithubRepository repository = new GithubRepository();
        repository.setUser("whiteship");
        repository.setName("live-study");

        GithubIssue githubIssue = new GithubIssue(repository);
        githubIssue.setId(1);
        githubIssue.setTitle("1주차 과제: JVM은 무엇이며 자바 코드는 어떻게 실행하는 것인가.");

        String url = githubIssue.getUrl();
        System.out.println(url);

        GithubIssue clone = (GithubIssue) githubIssue.clone();
        System.out.println(clone.getUrl());

        System.out.println(clone != githubIssue);
        System.out.println(clone.equals(githubIssue));
        System.out.println(clone.getClass() == githubIssue.getClass());
        System.out.println(clone.getRepository() == githubIssue.getRepository());

        System.out.println(clone.getUrl());

        List<String> tmp = new ArrayList<>();
        tmp.add("test");
        tmp.add("test2");
        List<String> tmp2 = new ArrayList<>(tmp); //shallow copy
        // 얕은 복사는 참조를 공유하므로, immutable 객체면 문제가 없지만 mutable 객체면 변경 전파(side effect) 위험이 있다.

    }

}
