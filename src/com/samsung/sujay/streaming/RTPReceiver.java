package com.samsung.sujay.streaming;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sujay on 20/8/16.
 */
public class RTPReceiver implements Runnable
{
    private DatagramSocket receiverSocket;
    private ExecutorService executorService;
    private int port;
    private volatile boolean isReceiving, isSetup;

    public RTPReceiver()
    {
        executorService = Executors.newSingleThreadExecutor();
        isReceiving = false;
        isSetup = false;
    }

    public void setup(int port)
    {
        try {
            receiverSocket = new DatagramSocket(null);
            receiverSocket.setReuseAddress(true);
            receiverSocket.bind(new InetSocketAddress(port));
            isSetup = true;
        } catch (SocketException e) {
            e.printStackTrace();
            isSetup = false;
        }

    }

    public void startReceiving()
    {
        isReceiving = true;
        if (isSetup)
            executorService.execute(this);
    }

    public void tearDown()
    {
        stopReceiving();
        isSetup = false;
    }

    public void waitForReceiver()
    {
    }

    public void stopReceiving()
    {
        isReceiving = false;
    }

    @Override
    public void run() {
        byte buf[] = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf,4096);
        while (isReceiving)
        {
            try {
                receiverSocket.receive(packet);
                Log("RECEIVED", packet.getData().length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        receiverSocket.close();
    }

    private void Log(String TYPE, int MSG) {
        if (true)
        {
            System.out.println(TYPE);
            System.out.println();
            System.out.println(MSG);
        }
    }
}
