package com.unocode.greedy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Q13305 {

    static int cityNumber;
    static int[] distances;
    static int[] gasPrices;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        cityNumber = Integer.parseInt(br.readLine());

        distances = new int[cityNumber-1];
        gasPrices = new int[cityNumber];

        StringTokenizer stD = new StringTokenizer(br.readLine());
        for (int i = 0 ; i < cityNumber - 1 ; i++) {
            distances[i] = Integer.parseInt(stD.nextToken());
        }
        StringTokenizer stP = new StringTokenizer(br.readLine());
        for (int i = 0 ; i < cityNumber ; i++) {
            gasPrices[i] = Integer.parseInt(stP.nextToken());
        }

        long minPrice = gasPrices[0]; //오버플로우 방지
        long minResult = 0; //오버플로우 방지

        for (int i = 0 ; i < cityNumber - 1 ; i++) {
            minPrice = Math.min(minPrice, gasPrices[i]);
            minResult += minPrice * distances[i];
        }

        System.out.println(minResult);
    }
}
