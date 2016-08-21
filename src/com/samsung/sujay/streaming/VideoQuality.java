package com.samsung.sujay.streaming;

/**
 * Created by sujay on 20/8/16.
 */
public class VideoQuality
{
    public int resolutionH, resolutionW, fps, bitrate;

    public VideoQuality (int resolutionW, int resolutionH, int fps, int bitrate)
    {
        this.bitrate     = bitrate;
        this.fps         = fps;
        this.resolutionH = resolutionH;
        this.resolutionW = resolutionW;
    }
}
