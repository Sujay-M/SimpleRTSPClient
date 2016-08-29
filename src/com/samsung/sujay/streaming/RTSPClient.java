package com.samsung.sujay.streaming;

import java.io.*;
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
    private Process ffmpegRTPReceiver;
    private ProcessBuilder ffmpegRTPReceiverBuilder;
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
                    String sdp = new String(content);
                    System.out.println(sdp);
                    controlString = getControlString(sdp);
                    String modSdp = modifySdp(sdp);
                    System.out.println(modSdp);
                }
            }

            callback.rtspData(type, null);

            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CustomException e) {
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

    private String modifySdp(String receivedSDP) throws CustomException {
        Pattern parseSDP1 = Pattern.compile("m=video (\\d+) .*");
        Pattern parseSDP2 = Pattern.compile("m=audio (\\d+) .*");
        Matcher vidMatcher = parseSDP1.matcher(receivedSDP);
        Matcher audMatcher = parseSDP2.matcher(receivedSDP);
        if (!vidMatcher.find())
            throw new CustomException("NO VIDEO DESCRIPTION");
        int v = vidMatcher.start();
        String videoInfo, info;
        if (audMatcher.find())
        {
            int a = audMatcher.start();
            if (a>v)
            {
                info      = receivedSDP.substring(0, v);
                videoInfo = receivedSDP.substring(v, a);
            }
            else
            {
                info      = receivedSDP.substring(0,a);
                videoInfo = receivedSDP.substring(v);
            }
        }
        else
        {
            info      = receivedSDP.substring(0,v);
            videoInfo = receivedSDP.substring(v);
        }
        videoInfo = videoInfo.replaceFirst("\\d+", clientPorts[0]+"");
        return info+videoInfo;
    }

    private String processVideoParams(String mediaParams)
    {
        String mediaDetails = mediaParams.replaceFirst("\\d+", ""+clientPorts[0])
                                         .replaceFirst("^a=control.*$", "");
        return mediaDetails;
    }

    private ProcessBuilder buildProcess(File sdp)
    {

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
//        rtpreceiver.stopReceiving();                                  destroy subprocess
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


}

