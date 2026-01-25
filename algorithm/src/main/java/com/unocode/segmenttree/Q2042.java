package com.unocode.segmenttree;

import java.io.*;
import java.util.*;

public class Q2042 {
    static int N, M, K;
    static long[] arr;       // 실제 수열 값
    static long[] fenwick;   // Fenwick Tree

    // Fenwick 초기화 (길이 N)
    static void initFenwick(int n) {
        fenwick = new long[n + 1];
    }

    // Fenwick Tree에서 idx에 value 더하기 (update)
    static void update(int idx, long diff) {
        for (int i = idx; i <= N; i += (i & -i)) {
            fenwick[i] += diff;
        }
    }

    // Fenwick Tree에서 1~idx까지 누적 합 구하기
    static long prefixSum(int idx) {
        long sum = 0;
        for (int i = idx; i > 0; i -= (i & -i)) {
            sum += fenwick[i];
        }
        return sum;
    }

    // 구간 합
    static long rangeSum(int left, int right) {
        return prefixSum(right) - prefixSum(left - 1);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        N = Integer.parseInt(st.nextToken());
        M = Integer.parseInt(st.nextToken());
        K = Integer.parseInt(st.nextToken());

        arr = new long[N + 1];
        initFenwick(N);

        // 입력 수열
        for (int i = 1; i <= N; i++) {
            long value = Long.parseLong(br.readLine());
            arr[i] = value;
            update(i, value);
        }

        StringBuilder sb = new StringBuilder();

        // M+K 명령 처리
        for (int i = 0; i < M + K; i++) {
            st = new StringTokenizer(br.readLine());
            int type = Integer.parseInt(st.nextToken());
            int b = Integer.parseInt(st.nextToken());
            long c = Long.parseLong(st.nextToken());

            if (type == 1) {
                // A[b] = c 로 update
                long diff = c - arr[b];
                arr[b] = c;
                update(b, diff);

            } else if (type == 2) {
                // 구간 합 출력
                sb.append(rangeSum(b, (int)c)).append('\n');
            }
        }
        System.out.print(sb);
    }
}
