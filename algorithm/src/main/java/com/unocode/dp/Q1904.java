package com.unocode.dp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Q1904 {

    static final int MOD = 15746;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int length = Integer.parseInt(br.readLine());

        if (length == 1) {
            System.out.print(1);
            return;
        }
        if (length == 2) {
            System.out.print(2);
            return;
        }

        int prev1 = 1;
        int prev2 = 2;

        for (int i = 3; i <= length; i++) {
            int cur = (prev1 + prev2) % MOD;
            prev1 = prev2;
            prev2 = cur;
        }
        System.out.print(prev2);
    }
}
