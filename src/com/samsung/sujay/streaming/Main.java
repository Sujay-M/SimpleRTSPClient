package com.samsung.sujay.streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) {
        DLog.setDevMode(false);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
// write your code here
        try {
//            RTSPClientWrapper client = new RTSPClientWrapper(new String[]{"rtsp://192.168.42.129:11111?h264=200-20-320-240"}, 0, 60001, 60002);
            RTSPClientWrapper client = new RTSPClientWrapper(new String[]{"rtsp://localhost:11111/high"}, 0, 60001, 60002);
            client.startStreaming();
            br.readLine();
            client.stopStreaming();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CustomException e) {
            e.printStackTrace();
        }
    }
}
