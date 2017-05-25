package com.oliveapp.verify.sample.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.oliveapp.face.liboffline_face_verification.FaceVerifier;
import com.oliveapp.face.liboffline_face_verification.nativecode.FaceFeature;
import com.oliveapp.face.liboffline_face_verification.nativecode.FeatureExtractResultList;
import com.oliveapp.face.liboffline_face_verification.nativecode.FeatureExtractionOption;
import com.oliveapp.face.liboffline_face_verification.nativecode.ImageType;
import com.oliveapp.face.liboffline_face_verification.nativecode.LivenessPackage;
import com.oliveapp.face.liboffline_face_verification.nativecode.VerifyResult;
import com.oliveapp.verify.sample.liveness.SampleResultActivity;
import com.oliveapp.verify.sample.model.FaceVerifyResult;

import java.io.IOException;

/******************************************
 * 跨进程前台服务，提供FaceVerifier相关服务
 * 主要包含：
 * 1. 抽取特征
 * 2. 比对特征
 * 3. 解析图像数据
 *****************************************/
public class FaceService extends Service {

    private static final String TAG = FaceService.class.getSimpleName();

    private FaceVerifier mFaceVerifier;
    private boolean isInitialized;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, new Notification());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    initialize();
                } catch (RuntimeException e) {
                    // ignore
                }
            }
        }).start();
    }

    private synchronized void initialize() {
        if (isInitialized) return;

        try {
            ServiceFactory.init(getApplicationContext());
            mFaceVerifier = ServiceFactory.getInstance().getFaceVerifier();
            isInitialized = true;
        } catch (Exception e) {
            isInitialized = false;
            Log.e(TAG, "Initialize Service Failure", e);
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mServiceBinder;
    }

    private void checkRtn(String message, int rtn) {
        if (rtn == 0) return;
        throw new RuntimeException(message + ", rtn = " + rtn);
    }

    private final IRemoteService.Stub mServiceBinder = new IRemoteService.Stub() {

        @Override
        public byte[] extractFeature(byte[] picByte, int picType) throws RemoteException {
            /****************************************************
             *
             * #  提取图像特征核心代码
             *    1.实例一个FeatureExtractionOption参数对象，并设置参数
             *    2.实例一个FeatureExtractResultList特征结果对象
             *    3.执行FaceVerifier.extractFeatureFromImageContent()方法，传入参数为图像数据，参数对像，特征结果对象
             *    4.在FeatureExtractResultList特征结果对象中，得到FaceFeature图像特征对象
             *
             ****************************************************/

            if (!isInitialized) initialize();

            // 设置option参数
            FeatureExtractionOption opt = new FeatureExtractionOption();
            FeatureExtractResultList res = new FeatureExtractResultList(); // 特征结果类

            try {
                opt.setEnableAutoFlip(false);
                opt.setEnableAutoRotate(false);
                opt.setMaxFacesAllowed(1);
                // 若是捕获到的照片，设置ImageType为IMAGETYPE_LEIZHENGJIANZHAO;若是芯片照，设置ImageType为IMAGETYPE_XINPIANZHAO
                opt.setImageType(picType == SampleResultActivity.PHOTO_IMAGE ? ImageType.IMAGETYPE_LEIZHENGJIANZHAO : ImageType.IMAGETYPE_XINPIANZHAO);
                opt.setIsQueryImage(picType == SampleResultActivity.PHOTO_IMAGE); // 若是捕获到的照片，设置为1;若是芯片照，设置为0

                Log.d(TAG, "Start extracing feature");
                int rtn = mFaceVerifier.extractFeatureFromImageContent(picByte, opt, res); // 图片数据，参数，结果
                checkRtn("face verify failed", rtn);
                // 如果特征提取结果为空，对用户进行相关提示
                if (res.isEmpty()) {
                    return null;
                } else {
                    return mFaceVerifier.serializeFeature(res.get(0).getFeature());
                }
            } finally {
                opt.delete();
                res.delete();
            }
        }

        @Override
        public byte[] extractFeatureFromPackage(byte[] packByte, int index, int picType) throws RemoteException {
            /****************************************************
             *
             * #  提取图像特征核心代码
             *    1.实例一个FeatureExtractionOption参数对象，并设置参数
             *    2.实例一个FeatureExtractResultList特征结果对象
             *    3.执行FaceVerifier.extractFeatureFromImageContent()方法，传入参数为图像数据，参数对像，特征结果对象
             *    4.在FeatureExtractResultList特征结果对象中，得到FaceFeature图像特征对象
             *
             ****************************************************/

            if (!isInitialized) initialize();

            LivenessPackage livenessPackage = new LivenessPackage();
            FeatureExtractionOption opt = new FeatureExtractionOption();
            FeatureExtractResultList res = new FeatureExtractResultList(); // 特征结果类

            try {
                int rtn = mFaceVerifier.parsePackageData(packByte, livenessPackage);
                checkRtn("parse package data failed", rtn);

                // 设置option参数
                opt.setEnableAutoFlip(false);
                opt.setEnableAutoRotate(false);
                opt.setMaxFacesAllowed(1);
                // 若是捕获到的照片，设置ImageType为IMAGETYPE_LEIZHENGJIANZHAO;若是芯片照，设置ImageType为IMAGETYPE_XINPIANZHAO
                opt.setImageType(picType == SampleResultActivity.PHOTO_IMAGE ? ImageType.IMAGETYPE_LEIZHENGJIANZHAO : ImageType.IMAGETYPE_XINPIANZHAO);
                opt.setIsQueryImage(picType == SampleResultActivity.PHOTO_IMAGE); // 若是捕获到的照片，设置为1;若是芯片照，设置为0

                Log.d(TAG, "Start extracing feature");
                rtn = mFaceVerifier.extractFeatureFromPackage(livenessPackage, index, opt, res); // 图片数据，参数，结果
                checkRtn("extract feature failed", rtn);
                // 如果特征提取结果为空，对用户进行相关提示
                if (res.isEmpty()) {
                    return null;
                } else {
                    return mFaceVerifier.serializeFeature(res.get(0).getFeature());
                }
            } finally {
                livenessPackage.delete();
                opt.delete();
                res.delete();
            }
        }

        @Override
        public FaceVerifyResult verifyFeature(byte[] feature1, byte[] feature2) throws RemoteException {
            /*************************************************
             *
             * #  比对核心代码
             *    1.实例化为VerifyResult比对结果类
             *    2.调用faceVerifier.verifyFeature()方法，其参数分别为捕获照图像特征，芯片照图像特征及比对结果对象
             *    3.利用封装的Parcelable对象回传数据
             *
             *************************************************/

            if (!isInitialized) initialize();

            VerifyResult verifyResult = new VerifyResult();
            FaceFeature faceFeature1 = null;
            FaceFeature faceFeature2 = null;
            try {
                faceFeature1 = mFaceVerifier.deserializeFeature(feature1);
                faceFeature2 = mFaceVerifier.deserializeFeature(feature2);
                int rtn = mFaceVerifier.verifyFeature(faceFeature1, faceFeature2, verifyResult);
                checkRtn("verify feature failed", rtn);
                return new FaceVerifyResult(verifyResult.getIsSamePerson(), verifyResult.getSimilarity());
            } finally {
                verifyResult.delete();
                if (faceFeature1 != null) {
                    faceFeature1.delete();
                }
                if (faceFeature2 != null) {
                    faceFeature2.delete();
                }
            }
        }

        @Override
        public byte[] getFrameFromPackageData(byte[] packageData) throws RemoteException {

            if (!isInitialized) initialize();

            LivenessPackage pack = new LivenessPackage();
            try {
                int rtn = mFaceVerifier.parsePackageData(packageData, pack); // 解析数据
                if (rtn != 0) return null;

                return mFaceVerifier.getImageInPackage(pack, 0); // 获取人像图片数据
            } finally {
                pack.delete();
            }
        }

        @Override
        public boolean isInitialized() throws RemoteException {
            if (!isInitialized) initialize();
            return isInitialized;
        }
    };
}
