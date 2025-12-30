package com.unocode.binarysearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Q2805 {

    static int treeNumber;
    static long requiredLength;
    static int[] treeHeights;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        treeNumber = Integer.parseInt(st.nextToken());
        requiredLength = Long.parseLong(st.nextToken());

        treeHeights = new int[treeNumber];

        st = new StringTokenizer(br.readLine());

        int maxHeight = 0;
        for (int i = 0; i < treeNumber; i++) {
            treeHeights[i] = Integer.parseInt(st.nextToken());
            maxHeight = Math.max(maxHeight, treeHeights[i]);
        }

        int left = 0;
        int right = maxHeight;
        int answer = 0;

        while (left <= right) {
            int mid = (left + right) / 2;
            long sum = 0;

            for (int h : treeHeights) {
                if (h > mid) {
                    sum += (h - mid);
                }
            }

            if (sum >= requiredLength) {
                answer = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        System.out.println(answer);
    }
}
