package com.unocode.unionfind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Set;

public class Q28277 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        int N = Integer.parseInt(st.nextToken());
        int Q = Integer.parseInt(st.nextToken());

        @SuppressWarnings("unchecked")
        Set<Integer>[] sets = new Set[N + 1];
        for (int i = 1; i <= N; i++) {
            sets[i] = new HashSet<>();
            st = new StringTokenizer(br.readLine());
            int size = Integer.parseInt(st.nextToken());
            for (int j = 0; j < size; j++) {
                int val = Integer.parseInt(st.nextToken());
                sets[i].add(val);
            }
        }

        StringBuilder sb = new StringBuilder();
        while (Q-- > 0) {
            st = new StringTokenizer(br.readLine());
            int op = Integer.parseInt(st.nextToken());

            if (op == 1) {
                int a = Integer.parseInt(st.nextToken());
                int b = Integer.parseInt(st.nextToken());
                if (sets[a].size() < sets[b].size()) {
                    // swap small and large
                    Set<Integer> tmp = sets[a];
                    sets[a] = sets[b];
                    sets[b] = tmp;
                }
                // merge b into a
                sets[a].addAll(sets[b]);
                sets[b].clear();

            } else {
                int a = Integer.parseInt(st.nextToken());
                sb.append(sets[a].size()).append('\n');
            }
        }

        System.out.print(sb.toString());
    }
}
