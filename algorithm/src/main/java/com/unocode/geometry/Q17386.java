package com.unocode.geometry;

import java.io.*;
import java.util.*;

public class Q17386 {

    static class Point {
        long x, y;
        Point(long x, long y) {
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st;

        st = new StringTokenizer(br.readLine());
        Point A = new Point(Long.parseLong(st.nextToken()),
                Long.parseLong(st.nextToken()));
        Point B = new Point(Long.parseLong(st.nextToken()),
                Long.parseLong(st.nextToken()));

        st = new StringTokenizer(br.readLine());
        Point C = new Point(Long.parseLong(st.nextToken()),
                Long.parseLong(st.nextToken()));
        Point D = new Point(Long.parseLong(st.nextToken()),
                Long.parseLong(st.nextToken()));

        int ccw1 = ccw(A, B, C);
        int ccw2 = ccw(A, B, D);
        int ccw3 = ccw(C, D, A);
        int ccw4 = ccw(C, D, B);

        if (ccw1 * ccw2 < 0 && ccw3 * ccw4 < 0) {
            System.out.println(1);
        } else {
            System.out.println(0);
        }
    }

    static int ccw(Point p1, Point p2, Point p3) {
        //외적으로 방향성을 구한다. 넓이는 필요 없음
        long cross = (p2.x - p1.x) * (p3.y - p1.y)
                - (p3.x - p1.x) * (p2.y - p1.y);
        if (cross > 0) return 1;
        if (cross < 0) return -1;
        return 0;
    }
}
