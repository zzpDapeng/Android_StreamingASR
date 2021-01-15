package com.dreamfish.audiorecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class Utils {

    public static int[] bytesToInts(byte[] bytes){
        int bytesLength=bytes.length;
        int[] ints=new int[bytesLength%4==0? bytesLength/4:bytesLength/4+1];
        int lengthFlag=4;
        while (lengthFlag<=bytesLength){
            ints[lengthFlag/4-1]=(bytes[lengthFlag-4]<<24)|(bytes[lengthFlag-3]&0xff)<<16|
                    (bytes[lengthFlag-2]&0xff)<<8|(bytes[lengthFlag-1]&0xff);
            lengthFlag+=4;
        }
        for (int i=0;i<bytesLength+4-lengthFlag;i++){
            if (i==0) ints[lengthFlag/4-1]|=bytes[lengthFlag-4+i]<<8*(bytesLength+4-lengthFlag-i-1);
            else ints[lengthFlag/4-1]|=(bytes[lengthFlag-4+i]&0xff)<<8*(bytesLength+4-lengthFlag-i-1);
        }
        return ints;
    }

    /**
     * 录制的音频为16位,2个byte，因此将byte[]数组转换为short[]数组
     * @param bytes
     * @return
     */
    public static short[] bytesToShorts(byte[] bytes){
        int bytesLength=bytes.length;
        int shortLength = bytesLength%2==0? bytesLength/2:bytesLength/2+1;
        short[] shorts=new short[shortLength];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, bytesLength);
        ShortBuffer shortBuffer = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        shortBuffer.get(shorts, 0, shortLength);
        return shorts;
    }
}
