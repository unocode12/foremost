package com.unocode.dp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Stack;
import java.util.StringTokenizer;

//https://www.acmicpc.net/problem/14003
//14002번 문제와 동일하지만 더 빠르다. - 최장 증가 부분 수열
public class Q14003 {

    static int[] array;
    static int[] lis;
    static int[] lisIndex;
    static int[] beforeIndex;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int length = Integer.parseInt(br.readLine());
        array = new int[length];
        lis = new int[length];
        lisIndex = new int[length];
        beforeIndex = new int[length];
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");
        for (int i = 0 ; i < length ; i++) {
            array[i] = Integer.parseInt(st.nextToken());
            beforeIndex[i] = -1;
        }

        int lisLength = 0;
        for (int j = 0 ; j < length ; j++) {
            int position = lowerBound(array[j], lis, lisLength);
            lis[position] = array[j];
            lisIndex[position] = j;

            if (position > 0) {
                beforeIndex[j] = lisIndex[position-1];
            }

            if (position == lisLength) lisLength++;
        }

        Stack<Integer> stack = new Stack<>();
        int idx = lisIndex[lisLength - 1];
        while (idx != -1) {
            stack.push(array[idx]);
            idx = beforeIndex[idx];
        }

        System.out.println(lisLength);

        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.append(stack.pop()).append(" ");
        }
        System.out.println(sb);
    }

    static int lowerBound(int target, int[] list, int length) {
        int left = 0;
        int right = length;

        while (left < right) {
            int mid = (left + right) / 2;
            if (list[mid] < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }
}
