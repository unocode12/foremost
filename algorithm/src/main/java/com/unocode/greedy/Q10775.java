package com.unocode.greedy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Q10775 {

    static int G, P;
    static int[] parent;

    static int find(int x) {
        if (parent[x] == x) return x;
        return parent[x] = find(parent[x]);
    }

    static void union(int x, int y) {
        x = find(x);
        y = find(y);
        parent[x] = y;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        G = Integer.parseInt(br.readLine()); // 게이트 수
        P = Integer.parseInt(br.readLine()); // 비행기 수

        parent = new int[G + 1];

        // 초기 상태: 각 게이트는 자기 자신이 루트
        for (int i = 0; i <= G; i++) {
            parent[i] = i;
        }

        int count = 0;

        for (int i = 0; i < P; i++) {
            int gi = Integer.parseInt(br.readLine());

            // gi 이하에서 가장 큰 사용 가능한 게이트
            int availableGate = find(gi);

            // 더 이상 도킹 불가능
            if (availableGate == 0) {
                break;
            }

            count++;

            union(availableGate, availableGate - 1);
        }

        System.out.println(count);
    }
}