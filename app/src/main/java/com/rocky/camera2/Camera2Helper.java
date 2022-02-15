package com.rocky.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static android.graphics.ImageFormat.YUV_420_888;

/**
 * <pre>
 *     author : rocky
 *     time   : 2022/02/14
 * </pre>
 */
public class Camera2Helper {
    private Context context;
    private TextureView mTextureView;
    private Size mPreviewSize;
    private Point previewViewSize;
    private ImageReader mImageReader;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Camera2Listener camera2Listener;
    private CameraId cameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;


    public Camera2Helper(Context context) {
        this.context = context;
    }

    public Camera2Helper(Context context, Camera2Listener listener) {
        this.context = context;
        camera2Listener = listener;
    }

    public enum CameraId {
        REAR("0"),//后置
        FRONT("1");//前置
        String value;

        CameraId(String value) {
            this.value = value;
        }
    }

    public synchronized void start(TextureView textureView, CameraId cameraId) {
        mTextureView = textureView;
        this.cameraId = cameraId;
        //camera2 打开摄像头的是通过系统服务
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        //获取摄像头有的配置信息
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId.value);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //寻找一个合适的尺寸
            mPreviewSize = getBestSupportedSize(new ArrayList<Size>(Arrays.asList(map.getOutputSizes(SurfaceTexture.class))));
            mImageReader = ImageReader.newInstance(
                    mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    YUV_420_888,
                    2
            );
            //处理数据 设置在子线程
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
            mImageReader.setOnImageAvailableListener(new OnImageAvailableListenerImpl(), mBackgroundHandler);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId.value, mDeviceStateCallback, mBackgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Size getBestSupportedSize(List<Size> sizes) {
        Point maxPreviewSize = new Point(1920, 1080);
        Point minPreviewSize = new Point(1280, 720);
        Size defaultSize = sizes.get(0);
        Size[] tempSizes = sizes.toArray(new Size[0]);
        Arrays.sort(tempSizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                if (o1.getWidth() > o2.getWidth()) {
                    return -1;
                } else if (o1.getWidth() == o2.getWidth()) {
                    return o1.getHeight() > o2.getHeight() ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        sizes = new ArrayList<>(Arrays.asList(tempSizes));
        for (int i = sizes.size() - 1; i >= 0; i--) {
            if (maxPreviewSize != null) {
                if (sizes.get(i).getWidth() > maxPreviewSize.x || sizes.get(i).getHeight() > maxPreviewSize.y) {
                    sizes.remove(i);
                    continue;
                }
            }
            if (minPreviewSize != null) {
                if (sizes.get(i).getWidth() < minPreviewSize.x || sizes.get(i).getHeight() < minPreviewSize.y) {
                    sizes.remove(i);
                }
            }
        }
        if (sizes.size() == 0) {
            return defaultSize;
        }
        Size bestSize = sizes.get(0);
        float previewViewRatio;
        if (previewViewSize != null) {
            previewViewRatio = (float) previewViewSize.x / (float) previewViewSize.y;
        } else {
            previewViewRatio = (float) bestSize.getWidth() / (float) bestSize.getHeight();
        }

        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio;
        }

        for (Size s : sizes) {
            if (Math.abs((s.getHeight() / (float) s.getWidth()) - previewViewRatio) < Math.abs(bestSize.getHeight() / (float) bestSize.getWidth() - previewViewRatio)) {
                bestSize = s;
            }
        }
        return bestSize;
    }

    private class OnImageAvailableListenerImpl implements ImageReader.OnImageAvailableListener {
        private byte[] y;
        private byte[] u;
        private byte[] v;

        //这个和camera 的onFrame 回掉作用一样
        @Override
        public void onImageAvailable(ImageReader reader) {
            //这里回调返回的是封装了的数据  camera 返回的是byte[]
            Image image = reader.acquireNextImage();
            //在这里处理数据
            Image.Plane[] planes = image.getPlanes();
            if (y == null) {
//                new  了一次
//                limit  是 缓冲区 所有的大小     position 起始大小
                y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
                u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
                v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
            }
            if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
//                分别填到 yuv

                planes[0].getBuffer().get(y);
                planes[1].getBuffer().get(u);
                planes[2].getBuffer().get(v);
//                yuv 420
            }
            if (camera2Listener != null) {
                camera2Listener.onPreview(y, u, v, mPreviewSize, planes[0].getRowStride(), cameraId);
            }

            image.close();//不调用  则渲染几帧之后就会结束渲染
        }
    }

    public interface Camera2Listener {
        /**
         * 预览数据回调
         *
         * @param y           预览数据，Y分量
         * @param u           预览数据，U分量
         * @param v           预览数据，V分量
         * @param previewSize 预览尺寸
         * @param stride      步长
         * @param cameraId    前后摄像头的表示
         */
        void onPreview(byte[] y, byte[] u, byte[] v, Size previewSize, int stride, CameraId cameraId);
    }

    public void setCamera2Listener(Camera2Listener camera2Listener) {
        this.camera2Listener = camera2Listener;
    }


    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            //摄像头打开之后 建立会话
            createCameraPreviewSession();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            //设置预览宽高
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            //创建以后surface 画面
            Surface surface = new Surface(texture);
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //预览的TexetureView
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            //            保存摄像头   数据  ---H264码流
            //  各种回调了
            //建立 链接     目的  几路 数据出口
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),mCaptureStateCallback,mBackgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private CameraCaptureSession mCaptureSession;
    private CameraCaptureSession.StateCallback mCaptureStateCallback = new CameraCaptureSession.StateCallback() {


        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
//            系统的相机
            // The camera is already closed
            if (null == mCameraDevice) {
                return;
            }
            mCaptureSession = session;
            try {
                mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                        new CameraCaptureSession.CaptureCallback() {
                        }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    public synchronized void openCamera() {



    }


}
