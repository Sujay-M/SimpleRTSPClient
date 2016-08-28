package com.samsung.sujay.streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sujay.m on 8/28/2016.
 */
public class RTSPClient
{

    interface MessageReceived
    {
        void rtspData(String type, byte[] data);
        void rtpDGram(DatagramPacket packet);
        void rtpStream(byte[] data);
    }

    private String rtspURL;
    private String serverIp;
    private int serverPort;
    private Socket RTSPSocket;
    private RTPReceiver rtpreceiver;
    private OutputStream outputStream;
    private BufferedReader readFromServer;
    private int CSeq;
    private boolean isConfigured;
    private String sessionId;
    private int[] clientPorts;
    private MessageReceived callback;
    private String controlString;

    public RTSPClient(MessageReceived callback, String url, int videoPort, int audioPort) throws IOException, CustomException {

        this.callback = callback;
        this.rtspURL = url;
        String address = parseAddress(url);
        if (address == null)
            throw new CustomException("Cannot Parse Address");
        String addrParts[] = address.split(":");
        this.serverIp = addrParts[0];
        this.serverPort = Integer.parseInt(addrParts[1]);
        this.CSeq = 1;
        this.isConfigured = false;
        clientPorts = new int[]{videoPort, audioPort};
        controlString = null;
        connect();
        rtpreceiver = new RTPReceiver(videoPort);
        rtpreceiver.startReceiving();
    }

    private void connect() throws IOException {
        InetAddress serverAddr = InetAddress.getByName(serverIp);
        RTSPSocket = new Socket(serverAddr, serverPort);
        RTSPSocket.setReuseAddress(true);
        outputStream = RTSPSocket.getOutputStream();
        readFromServer = new BufferedReader(new InputStreamReader(RTSPSocket.getInputStream(), "UTF8"));
    }

    private String parseAddress(String url) {
        Pattern rtspUrl = Pattern.compile("^rtsp://(.*):(\\d*).*$");
        Matcher matcher = rtspUrl.matcher(url);
        if (matcher.find())
            return matcher.group(1) +":"+matcher.group(2);
        return null;
    }

    private String getDescribeMsg()
    {
        return ("DESCRIBE "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: java\r\n"
                +"Accept: application/sdp\r\n\r\n");
    }

    private String getSetupMsg(String controlString)
    {
        return ("SETUP "+rtspURL+"/"+ controlString +" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: java\r\n"
                +"Transport: RTP/AVP;unicast;client_port="+clientPorts[0]+"-"+clientPorts[1]+"\r\n\r\n");
    }

    private String getPlayMsg()
    {
        return ("PLAY "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: java\r\n"
                +"Session: "+sessionId+"\r\n"
                +"Range: npt=0.000-\r\n\r\n");
    }

    private String getTeardownMsg()
    {
        return ("TEARDOWN "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: java\r\n"
                +"Session: "+sessionId+"\r\n\r\n");
    }

    private void sendRTSPCommand(String cmd)
    {
// DLog.Log("SENDING", cmd);
        CSeq++;
        try {
            outputStream.write(cmd.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String receiveRTSPReply(String type)
    {
        try {
            StringBuilder buf = new StringBuilder();
            String temp;
            Pattern pattern = Pattern.compile("Content-Length: (\\d+)");
            int contentLength = 0;
            while ((temp = readFromServer.readLine())!=null)
            {
                if (temp.isEmpty())
                    break;
                buf.append(temp).append("\r\n");
                Matcher match = pattern.matcher(temp);
                if (match.find())
                    contentLength = Integer.parseInt(match.group(1));
            }
// DLog.Log("RECEIVED", buf.toString());
            if (contentLength != 0)
            {
                char content[] = new char[contentLength+1];
                readFromServer.read(content, 0, contentLength+1);
// DLog.Log("CONTENT", new String(content));
                if (type.equalsIgnoreCase("DESCRIBE"))
                {
                    System.out.println(new String(content));
                    controlString = getControlString(new String (content));
                }
            }

            callback.rtspData(type, null);

            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getControlString(String sdp)
    {
        return null;
    }

    public void sendDescribe()
    {
        String describeMsg = getDescribeMsg();
        sendRTSPCommand(describeMsg);
        String reply = receiveRTSPReply("DESCRIBE");
        DLog.Log("DESCRIBE", reply);
    }

    private String getSessionId(String msg)
    {
        String[] lines = msg.split("\r\n");
        for (String line:lines)
        {
            String params[] = line.split(" ");
            if (params[0].equalsIgnoreCase("Session:"))
                return params[1].split(";")[0].trim();
        }
        return null;
    }

    private int[] getPorts(String type, String msg)
    {
// DLog.Log("Log", "GETPORTS");
        Pattern pattern = Pattern.compile(type+"=(\\d*)-(\\d*)");
        Matcher matcher = pattern.matcher(msg);
        while (matcher.find())
            return new int[]{Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2))};
        return null;
    }

    public void sendSetup()
    {
        String setupMsg;
        if (controlString == null)
            setupMsg = getSetupMsg("streamid=0");
        else
            setupMsg = getSetupMsg(controlString);
        sendRTSPCommand(setupMsg);
        String reply = receiveRTSPReply("SETUP");
        DLog.Log("SETUP", reply);
        sessionId = getSessionId(reply);
        isConfigured = true; //search for status ok and then set configured
    }

    public void sendPlay() throws CustomException {
        if (isConfigured) {
            String playMsg = getPlayMsg();
            sendRTSPCommand(playMsg);
            String reply = receiveRTSPReply("PLAY");
            DLog.Log("PLAY", reply);
        }

        else
            throw new CustomException("WRONG STATE");
    }

    public void sendTeardown() throws CustomException {
        if (!isConfigured)
            throw new CustomException("WRONG STATE");
        rtpreceiver.stopReceiving();
        String teardownMsg = getTeardownMsg();
        sendRTSPCommand(teardownMsg);
        String reply = receiveRTSPReply("TEARDOWN");
        DLog.Log("TEARDOWN", reply);
    }

    public void cleanup() throws IOException {
        readFromServer.close();
        outputStream.close();
        RTSPSocket.close();
    }

    class RTPReceiver implements Runnable
    {
        private int port;
        private DatagramSocket receiver;
        private volatile boolean isReceiving;
        private Thread receiverThread;

        RTPReceiver(int port) throws SocketException {
            receiver = new DatagramSocket(null);
            receiver.setReuseAddress(true);
            receiver.bind(new InetSocketAddress(port));
            this.port = port;
            isReceiving = false;
            receiverThread = new Thread(this);
        }

        void startReceiving()
        {
            isReceiving = true;
            receiverThread.start();
        }

        void stopReceiving()
        {
            isReceiving = false;
            receiverThread.interrupt();
        }

        @Override
        public void run() {

            byte buf[] = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buf,2048);
            short i = 1;
            long count = 0;
            long startTime = System.currentTimeMillis();

            while (isReceiving && !receiverThread.isInterrupted())
            {
                DLog.Log("THREAD", "WAITING TO RECEIVE");
                try {
                    receiver.receive(packet);
                    callback.rtpDGram(packet);
                    count += packet.getLength();
                    if (i % 30 == 0)
                    {
                        long endTime = System.currentTimeMillis();
                        double speed = (double)count/(double)(endTime-startTime);
                        double size = count/1000000.0;
                        System.out.format("\r%.2fKBps %.3fMB", speed, size);
                        i = 1;
                    }
                    i++;
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            receiver.close();

        }

        public boolean isReceiving() {
            return isReceiving;
        }
    }
}

