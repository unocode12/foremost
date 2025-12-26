package com.unocode.structural.adapter;

import com.unocode.structural.adapter.security.LoginHandler;
import com.unocode.structural.adapter.security.UserDetailsService;

public class Client {

    public static void main(String[] args) {
        AccountService accountService = new AccountService();
        UserDetailsService userDetailsService = new AccountUserDetailsService(accountService);
        LoginHandler loginHandler = new LoginHandler(userDetailsService);
        String login = loginHandler.login("hojin", "test123");
        System.out.println(login);
    }
}
