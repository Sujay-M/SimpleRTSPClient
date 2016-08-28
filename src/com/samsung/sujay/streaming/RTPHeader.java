package com.samsung.sujay.streaming;
/**
 * Created by sujay.m on 8/25/2016.
 */
public class RTPHeader
{
    byte version;
    boolean p;
    boolean x;
    byte cc;
    boolean m;
    byte pt;
    int sequenceNumber;
    long timeStamp;
    long syncSourceId;

    int rtpHeaderLength;

    byte NRI, nalUnit1, type, nalUnit2, nalUnit;
    boolean F, startBit, reservedBit, endBit;
    public void parseHeader(byte[] data)
    {
        int parsed = 0;
// String bitString = new BigInteger(1, Arrays.copyOfRange(data, 0, 12)).toString(2);
        String bitString = convertToBitString(data, 0, 12);
        version = Byte.parseByte(bitString.substring(0,2), 2);
        p = bitString.charAt(2) == '1';
        x = bitString.charAt(3) == '1';
        cc = Byte.parseByte(bitString.substring(4,8), 2);
        m = bitString.charAt(8) == '1';
        pt = Byte.parseByte(bitString.substring(9,16), 2);
        sequenceNumber = Integer.parseInt(bitString.substring(16, 32),2);
        timeStamp = Long.parseLong(bitString.substring(32, 65), 2);
        syncSourceId = Long.parseLong(bitString.substring(65, 96), 2);
        parsed = 12 + cc*4;
        if (x)
        {
            int length = Integer.parseInt(convertToBitString(data, parsed+2, parsed+4));
            parsed += 4 + 4*length;
        }
        rtpHeaderLength = parsed;


/*
First byte: [ 3 NAL UNIT BITS | 5 FRAGMENT TYPE BITS]
Second byte: [ START BIT | RESERVED BIT | END BIT | 5 NAL UNIT BITS]
Other bytes: [... VIDEO FRAGMENT DATA...]
*/
        String nal1BitString = convertToBitString(data, parsed, parsed+1);
        F = nal1BitString.charAt(0)=='1';
        NRI = Byte.parseByte(nal1BitString.substring(1, 3), 2);
        nalUnit1 = Byte.parseByte(nal1BitString.substring(0, 3), 2);
        type = Byte.parseByte(nal1BitString.substring(3, 8), 2);

        if (type==7 || type==8)
        {
//sps or pps packets
        }
        else
        {
            String nal2BitString = convertToBitString(data, parsed+1, parsed+2);
            startBit = nal2BitString.charAt(0) == '1';
            reservedBit = nal2BitString.charAt(1) == '1';
            endBit = nal2BitString.charAt(2) =='1';
            nalUnit2 = Byte.parseByte(nal2BitString.substring(3, 8), 2);
            nalUnit = Byte.parseByte(nal1BitString.substring(0, 3) + nal2BitString.substring(3, 8), 2);
        }

    }

    public String headerString() {
        return (version + " " + p + " " + x + "\n"
                + cc + " " + m + " "+ pt + "\n"
                + sequenceNumber + " " + timeStamp + " "
                + syncSourceId + "\n");
    }

    public String nalString()
    {
        return nalUnit1 + " " + type +" " + "\n"
                + startBit + " " + reservedBit + " " + endBit + " "+ nalUnit2;
    }

    private String convertToBitString (byte[] arr, int from, int to)
    {
        StringBuilder buf = new StringBuilder((to-from)*8);
        for (int i = from; i<to; i++)
            buf.append(("0000000" + Integer.toBinaryString(0xFF & arr[i])).replaceAll(".*(.{8})$", "$1"));
        return buf.toString();
    }
}
