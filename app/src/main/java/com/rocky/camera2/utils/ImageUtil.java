package com.rocky.camera2.utils;

import com.rocky.camera2.Camera2Helper;

import static com.rocky.camera2.Camera2Helper.CameraId.REAR;

/**
 * <pre>
 *     author : rocky
 *     time   : 2022/02/15
 * </pre>
 */
public class ImageUtil {
    public static void yuvToNv21(byte[] y, byte[] u, byte[] v, byte[] nv21, int stride, int height) {
        //把y直接放入数组
        System.arraycopy(y, 0, nv21, 0, y.length);
        // 注意，若length值为 y.length * 3 / 2 会有数组越界的风险，需使用真实数据长度计算
        int length = y.length + u.length / 2 + v.length / 2;
        //先放v 后方 u
        int uIndex = 0, vIndex = 0;
        for (int i = stride * height; i < length; i += 2) {
            nv21[i] = v[vIndex];
            nv21[i + 1] = u[uIndex];
            vIndex += 2;
            uIndex += 2;
        }
    }

    //根据摄像头的id（前后） 来 确定旋转的方向  后置摄像头 旋转顺时针旋转90 前置摄像头 逆时针旋转90
    public static void nv21_rotate_to_90(byte[] nv21, byte[] nv21_rotated, int width, int height, Camera2Helper.CameraId cameraId) {
        int y_size = width * height;
        int uvHeight = height >> 1;
        int k = 0;
        switch (cameraId) {
            case REAR:
                //先旋转y
                for (int i = 0; i < width; i++) {
                    for (int j = height - 1; j >= 0; j--) {
                        nv21_rotated[k++] = nv21[width * j + i];
                    }
                }
                //在放 vu
                for (int i = 0; i < width; i += 2) {
                    for (int j = uvHeight - 1; j >= 0; j--) {
                        nv21_rotated[k++] = nv21[y_size + width * j + i];
                        nv21_rotated[k++] = nv21[y_size + width * j + i + 1];
                    }
                }
                break;
            case FRONT:
                for (int i = width - 1; i >= 0; i--) {
                    for (int j = 0; j < height; j++) {
                        nv21_rotated[k++] = nv21[width * j + i];
                    }
                }

                for (int i = width - 1; i >= 0; i -= 2) {
                    for (int j = 0; j < uvHeight; j++) {
                        nv21_rotated[k++] = nv21[y_size+width * j + i];
                        nv21_rotated[k++] = nv21[y_size+width * j + i - 1];

                    }
                }
                break;
        }
    }

    public static byte[] nv21toNV12(byte[] nv21, byte[] nv12) {
        int size = nv21.length;
        nv12 = new byte[size];
        int len = size * 2 / 3;
        System.arraycopy(nv21, 0, nv12, 0, len);

        int i = len;
        while (i < size - 1) {
            nv12[i] = nv21[i + 1];
            nv12[i + 1] = nv21[i];
            i += 2;
        }

        return nv12;
    }
}
