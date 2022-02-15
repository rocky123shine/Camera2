package com.rocky.camera2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.rocky.camera2.utils.DecodePlayerLiveH265;
import com.rocky.camera2.utils.EncodePlayerLiveH265;
import com.rocky.camera2.utils.ImageUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements Camera2Helper.Camera2Listener, EncodePlayerLiveH265.EncodeCallback {
    private static final String TAG = "MainActivity";
    private TextureView textureView;
    Camera2Helper camera2Helper;
    private MediaCodec mediaCodec;
    private SurfaceView surfaceV;
    private Surface surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        //1.  使用textureView 实现相机的预览   与 surfaceView的区别是 前者在View树上 后这不在
        textureView = findViewById(R.id.texture_preview);
        //2.设置监听给TextureView
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        surfaceV = findViewById(R.id.surface);
        surfaceV.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                DecodePlayerLiveH265.initDecoder(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

    }

    private boolean checkPermissions() {
        boolean allGranted = true;
        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);
        }
        return allGranted;
    }


    //为了看清 具体的回掉 区分谁是谁的回掉 在这里声明一个变量
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        // surface 就绪  开启相机
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            initCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        //上线实时投屏 使用以下回掉
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void initCamera() {


        camera2Helper = new Camera2Helper(this, this);
        camera2Helper.start(textureView, Camera2Helper.CameraId.REAR);
    }

    //   先转成nv21   再转成  yuv420    n21 横着   1   竖着
    private byte[] nv21;//width  height
    byte[] nv21_rotated;
    byte[] nv12;

    //数据回掉
    @Override
    public void onPreview(byte[] y, byte[] u, byte[] v, Size previewSize, int stride, Camera2Helper.CameraId cameraId) {
        if (nv21 == null) {
//            实例化了一次
            nv21 = new byte[stride * previewSize.getHeight() * 3 / 2];
            nv21_rotated = new byte[stride * previewSize.getHeight() * 3 / 2];

        }
//        if (mediaCodec == null) {
//            initCodec(previewSize);
//        }

        //1.yuv数据 首先转换成nv21
        ImageUtil.yuvToNv21(y, u, v, nv21, stride, previewSize.getHeight());
        //2.数据 旋转  根据摄像头的id的旋转数据
        ImageUtil.nv21_rotate_to_90(nv21, nv21_rotated, stride, previewSize.getHeight(), cameraId);
        //3.数据转换
        //Nv12     yuv420
        byte[] temp = ImageUtil.nv21toNV12(nv21_rotated, nv12);
//        //输出成H264的码流
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        //查询可用buffer
//        int buffer_index = mediaCodec.dequeueInputBuffer(100_000);
//        if (buffer_index >= 0) {
//            //找到可用buffer 获取buffer
//            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(buffer_index);
//            //清空可用buffer数据
//            inputBuffer.clear();
//            //放入数据
//            inputBuffer.put(temp, 0, temp.length);
//            //把buffer放入队列
//            mediaCodec.queueInputBuffer(buffer_index, 0, temp.length, 0, 0);
//        }
//
//        //获取待处理的buffer
//        int outBuffer_index = mediaCodec.dequeueOutputBuffer(info, 100_000);
//        while (outBuffer_index >= 0) {
//            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outBuffer_index);
//            //获取要处理的数据
//            byte[] dealData = new byte[outputBuffer.remaining()];
//            outputBuffer.get(dealData);
//
//            dealFrame(outputBuffer, info);
//
//            //释放资源
//            mediaCodec.releaseOutputBuffer(outBuffer_index, false);
//            outBuffer_index = mediaCodec.dequeueOutputBuffer(info, 100_000);
//        }
        EncodePlayerLiveH265.initEncodePlayer(previewSize, this);
        EncodePlayerLiveH265.encode(temp);

    }

    @Override
    public void onCallback(byte[] data) {
        //编码后的数据返回
        DecodePlayerLiveH265.decode(data);
    }


//    private static void initCodec(Size size) {
//        try {
//            mediaCodec = MediaCodec.createEncoderByType("video/avc");
//
//            final MediaFormat format = MediaFormat.createVideoFormat("video/avc",
//                    size.getHeight(), size.getWidth());
//            //设置帧率  手动触发一个I帧
//            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);//15*2 =30帧
//            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000_000);
//            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);//2s一个I帧
//            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            mediaCodec.start();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }


}