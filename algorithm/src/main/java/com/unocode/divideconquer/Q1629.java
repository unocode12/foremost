package com.unocode.divideconquer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Q1629 {

    static long A, B, C;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        A = Long.parseLong(st.nextToken());
        B = Long.parseLong(st.nextToken());
        C = Long.parseLong(st.nextToken());

        System.out.println(powerMod(A, B));
    }

    static long powerMod(long a, long b) {
        if (b == 0) return 1; //a의 0승은 나머지가 1이다.

        long half = powerMod(a, b / 2);
        long result = (half * half) % C;

        if (b % 2 == 1) {
            result = (result * a) % C;
        }

        return result;
    }
}
