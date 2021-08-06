package com.masters.som.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class uMatrixApp {

    public static void main(String[] args) throws IOException {

        BufferedReader bufferedReader =
                new BufferedReader(new FileReader("uMatrixBefore.txt"));

        String[][] tiny = new String[3][3];
        float[][] big = new float[5][5];

       String i;
        while ((i = bufferedReader.readLine()) != null) {

            String[] line = i.split("#");
            String []xy = line[0].split(",");
            String[] ww = line[1].split(",");

            int x = Integer.parseInt(xy[0]);
            int y = Integer.parseInt(xy[1]);

            tiny[x][y] = ww.toString();
        }
        System.out.println(tiny);
    }
}

