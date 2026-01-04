package com.unocode.dp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Stack;
import java.util.StringTokenizer;

//https://www.acmicpc.net/problem/14002
public class Q14002 {

    static int[] array;
    static int[] longestLengthArray;
    static int[] beforeIndex;
    static int longestLength = 0;
    static int longestIndex = -1;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int length = Integer.parseInt(br.readLine());
        array = new int[length];
        longestLengthArray = new int[length];
        beforeIndex = new int[length];
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");
        for (int i = 0 ; i < length ; i++) {
            array[i] = Integer.parseInt(st.nextToken());
            longestLengthArray[i] = 1;
            beforeIndex[i] = -1;
        }

        for (int j = 0 ; j < length ; j++) {
            for (int k = 0 ; k < j ; k++) {
                if (array[j] > array[k] && longestLengthArray[k] + 1 > longestLengthArray[j]) {
                    longestLengthArray[j] = longestLengthArray[k] + 1;
                    beforeIndex[j] = k;
                }
            }
            if (longestLengthArray[j] > longestLength) {
                longestLength = longestLengthArray[j];
                longestIndex = j;
            }
        }

        Stack<Integer> resultStack = new Stack<>();
        int tempIndex = longestIndex;
        while (tempIndex >= 0) {
            resultStack.push(array[tempIndex]);
            tempIndex = beforeIndex[tempIndex];
        }
        System.out.println(longestLength);
        StringBuilder sb = new StringBuilder();
        while (!resultStack.isEmpty()) {
            sb.append(resultStack.pop()).append(" ");
        }
        System.out.println(sb);
    }
}
