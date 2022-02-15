package com.rocky.camera2.utils;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Size;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <pre>
 *     author : rocky
 *     time   : 2022/02/15
 * </pre>
 */
public class EncodePlayerLiveH265 {

    private static MediaCodec mediaCodec;

    public static void initEncodePlayer(Size size,EncodeCallback encodeCallback) {
        callback = encodeCallback;
        if (mediaCodec != null) {
            return;
        }
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            final MediaFormat format = MediaFormat.createVideoFormat("video/avc",
                    size.getHeight(), size.getWidth());
            //设置帧率  手动触发一个I帧
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);//15*2 =30帧
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000_000);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);//2s一个I帧
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    static int frameIndex;
    //    1ms=1000us
    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / 15;
    }
    public static void encode(byte[] temp) {
        //输出成H264的码流
        //查询可用buffer
        int buffer_index = mediaCodec.dequeueInputBuffer(100_000);
        if (buffer_index >= 0) {
            //找到可用buffer 获取buffer
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(buffer_index);
            //清空可用buffer数据
            inputBuffer.clear();
            //放入数据
            inputBuffer.put(temp);
            //把buffer放入队列
            mediaCodec.queueInputBuffer(buffer_index, 0, temp.length, computePresentationTime(frameIndex), 0);
            frameIndex++;
        }

        //获取待处理的buffer
        MediaCodec.BufferInfo out_info = new MediaCodec.BufferInfo();
        int outBuffer_index = mediaCodec.dequeueOutputBuffer(out_info, 100_000);

        while (outBuffer_index >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outBuffer_index);
            //获取要处理的数据

            dealFrame(outputBuffer, out_info);

            //释放资源
            mediaCodec.releaseOutputBuffer(outBuffer_index, false);
            outBuffer_index = mediaCodec.dequeueOutputBuffer(out_info, 100_000);
        }
    }


    public static final int NAL_I = 19;
    public static final int NAL_VPS = 32;
    private static byte[] vps_sps_pps_buf;

    private static void dealFrame(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        int offset = 4;
        if (bb.get(2) == 0x01) {
            offset = 3;
        }
        int type = (bb.get(offset) & 0x7E) >> 1;
        if (type == NAL_VPS) {
            vps_sps_pps_buf = new byte[bufferInfo.size];
            bb.get(vps_sps_pps_buf);

        } else if (type == NAL_I) {
            final byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            byte[] newBuf = new byte[vps_sps_pps_buf.length + bytes.length];
            System.arraycopy(vps_sps_pps_buf, 0, newBuf, 0, vps_sps_pps_buf.length);
            System.arraycopy(bytes, 0, newBuf, vps_sps_pps_buf.length, bytes.length);
            if (callback != null) {
                callback.onCallback(newBuf);
            }
        } else {
            final byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            if (callback != null) {
                callback.onCallback(bytes);
            }

        }
    }
    private static EncodeCallback callback;

    public void setCallback(EncodeCallback callback) {
        this.callback = callback;
    }

    public interface EncodeCallback {
        void onCallback(byte[] data);
    }


    public static String writeContent(byte[] array) {
        char[] HEX_CHAR_TABLE = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
            sb.append(HEX_CHAR_TABLE[b & 0x0f]);
        }

        System.out.println(sb.toString());
        return sb.toString();
    }

}
