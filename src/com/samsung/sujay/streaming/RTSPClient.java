package com.samsung.sujay.streaming;

import com.samsung.sujay.streaming.RTPReceiver;
import com.samsung.sujay.streaming.VideoQuality;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sujay on 20/8/16.
 */
public class RTSPClient
{
    private static final boolean DEBUG = true;
    private String         URLFormat;
    private String         rtspURL;
    private String         serverIp;
    private int            serverPort;
    private VideoQuality   videoQuality;
    private Socket         RTSPSocket;
    private RTPReceiver    rtpreceiver;
    private PrintWriter    writeToServer;
    private BufferedReader readFromServer;
    private int            CSeq;
    private boolean        isStreaming;
    private boolean        isConfigured;
    private String         sessionId;
    int[]                  clientPorts;
    private ThreadPool     senderThreads;


    public RTSPClient(String URLFormat, String args[])
    {
        Log("LOG", "Constructor");
        this.URLFormat     = URLFormat;
        videoQuality   = parseAgruments(args);
        String address     = parseAddress(URLFormat);
        if (address == null)
            return; // throw exception
        String addrParts[] = address.split(":");
        this.serverIp      = addrParts[0];
        this.serverPort    = Integer.parseInt(addrParts[1]);
        Log("IP", serverIp);
        Log("PORT", serverPort+"");
        connect();
        this.CSeq          = 1;
        this.isConfigured  = false;
        this.isStreaming   = false;
        clientPorts        = new int[]{60784, 60785};
        senderThreads      = ThreadPool.getINSTANCE();
        rtpreceiver        = new RTPReceiver();
    }

    private void connect() {
        Log("LOG", "trying to connect");
        try
        {
            InetAddress serverAddr = InetAddress.getByName(serverIp);
            Log("TCP", "CONNECTING");
            RTSPSocket     = new Socket(serverAddr, serverPort);
            Log("TCP", "CONNECTED");
            RTSPSocket.setReuseAddress(true);
            writeToServer  = new PrintWriter(RTSPSocket.getOutputStream(),true);
            readFromServer = new BufferedReader(new InputStreamReader(RTSPSocket.getInputStream()));
            RTSPSocket.setTcpNoDelay(true);
        }
        catch (UnknownHostException e)
        {
            Log("ERROR", "Host Unreachable");
        }
        catch (IOException e)
        {
            Log("ERROR", "Cant Connect");
        }
    }

    private void Log(String TYPE, String MSG)
    {
        if (DEBUG)
        {
            System.out.println(TYPE);
            System.out.println(MSG);
        }
    }

    private String parseAddress(String urlFormat)
    {
        Pattern rtspUrl = Pattern.compile("^rtsp://(.*):(\\d*).*$");
        Matcher matcher = rtspUrl.matcher(urlFormat);
        if (matcher.find())
            return matcher.group(1) +":"+matcher.group(2);
        return null;
    }

    private VideoQuality parseAgruments(String[] args)
    {
        VideoQuality videoQuality = new VideoQuality(320,240,20,200);
        if (args != null)
        for (String arg:args)
        {
            switch (arg.substring(0,2))
            {
                case "-r":
                    String res[] = arg.substring(2).split("x");
                    videoQuality.resolutionW = Integer.parseInt(res[0]);
                    videoQuality.resolutionH = Integer.parseInt(res[1]);
                    break;
                case "-f":
                    videoQuality.fps = Integer.parseInt(arg.substring(2));
                    break;
                case "-b":
                    videoQuality.bitrate = Integer.parseInt(arg.substring(2));
                    break;
            }
        }
        rtspURL = URLFormat
                .replace("RESW", Integer.toString(videoQuality.resolutionW))
                .replace("RESH", Integer.toString(videoQuality.resolutionH))
                .replace("FPS" , Integer.toString(videoQuality.fps))
                .replace("BRATE",Integer.toString(videoQuality.bitrate));
        Log("URL", rtspURL);
        return videoQuality;
    }

    private String getOptionsMsg()
    {
        return ("OPTIONS "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n");
    }

    private String getDescribeMsg()
    {
        return ("DESCRIBE "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n"
                +"Accept: application/sdp\r\n\r\n");
    }

    private String getSetupMsg()
    {
        return ("SETUP "+rtspURL+"/trackID=1 RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n"
                +"Transport: RTP/AVP;unicast;client_port="+clientPorts[0]+"-"+clientPorts[1]+"\r\n\r\n");
    }

    private String getPlayMsg()
    {
        return ("PLAY "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n"
                +"Session: "+sessionId+"\r\n"
                +"Range: npt=0.000-\r\n\r\n");
    }

    private String getPauseMsg()
    {
        return ("PAUSE "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n"
                +"Session: "+sessionId+"\r\n");
    }

    private String getTeardownMsg()
    {
        return ("TEARDOWN "+rtspURL+" RTSP/1.0\r\n"
                +"CSeq: "+CSeq+"\r\n"
                +"User-Agent: python\r\n"
                +"Session: "+sessionId+"\r\n");
    }

    private void sendRTSPCommand(String cmd)
    {
        CSeq++;
        writeToServer.println(cmd);
    }

    private String receiveRTSPReply()
    {
        try {
            StringBuffer buf = new StringBuffer();
            String temp;
            do
            {
                temp = readFromServer.readLine();
                buf.append(temp + "\r\n");
                System.out.println(temp);
                Thread.sleep(100);
            } while (RTSPSocket.getInputStream().available() != 0);

            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sendOptions()
    {
        String optionsMsg = getOptionsMsg();
        sendRTSPCommand(optionsMsg);
        String reply = receiveRTSPReply();
        Log("OPTIONS", reply);
    }

    public void sendDescribe()
    {
        String describeMsg = getDescribeMsg();
        sendRTSPCommand(describeMsg);
        String reply = receiveRTSPReply();
        Log("DESCRIBE", reply);
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
        Log("LOG", "GETPORTS");
        Pattern pattern = Pattern.compile(type+"=(\\d*)-(\\d*)");
        Matcher matcher = pattern.matcher(msg);
        while (matcher.find())
            return new int[]{Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2))};
        return null;
    }


    public void sendSetup()
    {
        String setupMsg = getSetupMsg();
        sendRTSPCommand(setupMsg);
        String reply = receiveRTSPReply();
        Log("SETUP", reply);
        sessionId = getSessionId(reply);
        clientPorts = getPorts("client_port",reply);
        Log ("SESSIONID", sessionId);
        Log("CLIENTPORT", clientPorts[0]+" "+clientPorts[1]);
        rtpreceiver.setup(clientPorts[0]);
        isConfigured = true;
    }

    public void sendPlay()
    {
        isStreaming = true;
        String playMsg = getPlayMsg();
        sendRTSPCommand(playMsg);
        String reply = receiveRTSPReply();
        Log("PLAY", reply);
        if (isConfigured) {
            rtpreceiver.startReceiving();
        }
    }

    public void sendPause()
    {
        isStreaming = false;
        String pauseMsg = getPauseMsg();
        sendRTSPCommand(pauseMsg);
        String reply = receiveRTSPReply();
        Log ("PAUSE", reply);
    }

    public void sendTeardown()
    {
        isConfigured = false;
        String teardownMsg = getTeardownMsg();
        sendRTSPCommand(teardownMsg);
        String reply = receiveRTSPReply();
        Log("TEARDOWN", reply);
    }

    public void changeStreamParams()
    {

    }

    public void cleanup()
    {

    }

}
