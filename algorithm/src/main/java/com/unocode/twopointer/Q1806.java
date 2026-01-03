package com.unocode.twopointer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Q1806 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");
        int length = Integer.parseInt(st.nextToken());
        int targetSum = Integer.parseInt(st.nextToken());

        int[] array = new int[length];

        st = new StringTokenizer(br.readLine(), " ");
        for (int i = 0 ; i < length ; i++) {
            array[i] = Integer.parseInt(st.nextToken());
        }
        int startPoint = 0;
        int sum = 0;
        int minLength = Integer.MAX_VALUE;
        for (int endPoint = 0 ; endPoint < length ; endPoint++) {
            sum += array[endPoint];

            while (sum >= targetSum) {
                minLength = Math.min(minLength, endPoint - startPoint + 1);
                sum -= array[startPoint++];
            }
        }

        System.out.println(minLength == Integer.MAX_VALUE ? "0" : minLength);
    }
}
