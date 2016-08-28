package com.samsung.sujay.streaming;

/**
 * Created by sujay on 27/8/16.
 */
public class DLog {
    static boolean DEVMODE = true;

    public static void setDevMode(boolean b) {
        DEVMODE = b;
    }

    public static void Log(String error, String s) {
        if (DEVMODE)
            System.out.println(error + " : " + s);
    }
}
