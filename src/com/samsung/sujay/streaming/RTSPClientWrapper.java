package com.samsung.sujay.streaming;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

/**
 * Created by sujay.m on 8/28/2016.
 */
public class RTSPClientWrapper
{
    private String[] profileURLs;
    private int currentCriticality;
    private int videoPort;
    private int audioPort;
    private RTSPClient client;
    private RTSPHandler handler;

    RTSPClientWrapper(String profileURLs[], int currentCriticality, int videoPort, int audioPort) throws IOException, CustomException {
        this.profileURLs = profileURLs;
        this.currentCriticality = currentCriticality;
        this.videoPort = videoPort;
        this.audioPort = audioPort;
        handler = new RTSPHandler("test");
        client = new RTSPClient(handler, profileURLs[currentCriticality], videoPort, audioPort);
    }

    public void startStreaming() throws CustomException {
        client.sendDescribe();
        client.sendSetup();
        client.sendPlay();
    }

    public void stopStreaming() throws CustomException, IOException {
        client.sendTeardown();
        client.cleanup();
    }

    public void changeCriticality(int criticality) throws IOException, CustomException {
        currentCriticality = criticality;
        try {
            stopStreaming();
        } catch (CustomException e) {
        }
        client = new RTSPClient(handler, profileURLs[currentCriticality], videoPort, audioPort);
        startStreaming();
    }

    class RTSPHandler implements RTSPClient.MessageReceived
    {
        String clientName;
        DatagramSocket socket;

        RTSPHandler(String clientName)
        {
            this.clientName = clientName;
            try {
                this.socket = new DatagramSocket ();
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void rtspData(String type, byte[] data) {
// System.out.println("rtsp received");
        }

        @Override
        public void rtpDGram(DatagramPacket packet) {
// System.out.println("rtp received");
            byte[] rtpPacket = Arrays.copyOf(packet.getData(), packet.getLength());
            try {
                socket.send(new DatagramPacket(rtpPacket, packet.getLength(), InetAddress.getByName("127.0.0.1"), 12345));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void rtpStream(byte[] data) {

        }

    }
}
