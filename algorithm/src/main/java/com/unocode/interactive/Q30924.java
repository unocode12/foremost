package com.unocode.interactive;

import java.io.*;
import java.util.*;

public class Q30924 {
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    static PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));

    static int ask(char who, int value) throws IOException {
        out.println("? " + who + " " + value);
        out.flush();
        return Integer.parseInt(br.readLine());
    }

    public static void main(String[] args) throws Exception {
        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= 10000; i++) nums.add(i);

        Collections.shuffle(nums);

        int A = -1, B = -1;

        // A 찾기
        for (int x : nums) {
            if (ask('A', x) == 1) {
                A = x;
                break;
            }
        }

        // 다시 섞어서 B 찾기
        Collections.shuffle(nums);

        for (int x : nums) {
            if (ask('B', x) == 1) {
                B = x;
                break;
            }
        }

        // 정답 출력
        out.println("! " + (A + B));
        out.flush();
    }
}
