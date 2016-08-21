package com.samsung.sujay.streaming;

public class Main {

    public static void main(String[] args) {

        RTSPClient client = new RTSPClient("rtsp://192.168.1.3:1234?h264=BRATE-FPS-RESW-RESH",null);
        client.sendDescribe();
        client.sendSetup();
        client.sendPlay();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.sendTeardown();
    }
}
