package com.oliveapp.verify.sample.liveness.view_controller;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.oliveapp.camerasdk.PhotoModule;
import com.oliveapp.camerasdk.utils.CameraUtil;
import com.oliveapp.face.livenessdetectionviewsdk.event_interface.FaceCaptureViewUpdateEventHandlerIf;
import com.oliveapp.face.livenessdetectionviewsdk.uicomponents.CircleImageView;
import com.oliveapp.face.livenessdetectionviewsdk.uicomponents.GifImageView;
import com.oliveapp.face.livenessdetectionviewsdk.verification_controller.FaceCaptureController;
import com.oliveapp.face.livenessdetectionviewsdk.verification_controller.VerificationController;
import com.oliveapp.face.livenessdetectorsdk.datatype.AccessInfo;
import com.oliveapp.face.livenessdetectorsdk.livenessdetector.configs.ApplicationParameters;
import com.oliveapp.face.livenessdetectorsdk.livenessdetector.datatype.ImageProcessParameter;
import com.oliveapp.face.livenessdetectorsdk.livenessdetector.datatype.LivenessDetectionFrames;
import com.oliveapp.face.livenessdetectorsdk.livenessdetector.datatype.LivenessSessionState;
import com.oliveapp.face.livenessdetectorsdk.prestartvalidator.datatype.PrestartDetectionFrame;
import com.oliveapp.face.livenessdetectorsdk.utilities.utils.LogUtil;
import com.oliveapp.face.livenessdetectorsdk.utilities.utils.PackageNameManager;

import junit.framework.Assert;

import com.realidfarm.alsfarm.R;
/**
 * ViewController 实现了主要的界面逻辑
 * 如果需要定义界面，请继承此类编写自己的Activity，并自己实现事件响应函数
 * 可参考SampleAPP里的ExampleLivenessActivity
 */
public abstract class FaceCaptureMainActivity extends Activity implements FaceCaptureViewUpdateEventHandlerIf {
    public static final String TAG = FaceCaptureMainActivity.class.getSimpleName();
    public static final int REQUEST_CODE = 10001;

    // Camera Preview Parameter
    private static final float TARGET_PREVIEW_RATIO = 4 / 3f; // 摄像头Previwe预览界面的长宽比 默认使用4:3
    private static final int MAX_PREVIEW_WIDTH = 961; // 摄像头Preview预览界面的最大宽度，默认使用分辨率1280x960是可以平衡预览质量和处理速度的
    // Customer
    private AccessInfo mAccessInfo = AccessInfo.getInstance().setAccessInfo("testid", "testid");

    private String mPackageName; // 包名
    private PhotoModule mPhotoModule; // 摄像头模块

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!PackageNameManager.isPackageNameSet()) {
            PackageNameManager.setPackageName(getPackageName());
        }
        com.oliveapp.camerasdk.utils.PackageNameManager.setPackageName(PackageNameManager.getPackageName());
        increaseClassObjectCount();

        Log.i(TAG, "[BEGIN] FaceCaptureMainActivity::onCreate()");
        super.onCreate(savedInstanceState);
        mPackageName = getPackageName();

        // 初始化界面元素
        initViews();
        // 初始化摄像头
        initCamera();
        // 初始化检测逻辑控制器(VerificationController)
        initControllers();

        Log.i(TAG, "[END] FaceCaptureMainActivity::onCreate()");
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "[BEGIN] FaceCaptureMainActivity::onResume()");
        super.onResume();

        if (mPhotoModule != null) {
            mPhotoModule.onResume();
            // 设置摄像头回调，自此之后VerificationController.onPreviewFrame函数就会源源不断的收到摄像头的数据
        } else {
            // 如果按了HOME键或中间被打断，直接判定为CANCALLED
            onPrestartFail(LivenessSessionState.SESSION_CANCELLED);
        }

        try {
            mPhotoModule.setPreviewDataCallback(mFaceCaptureController, mCameraHandler);
        } catch (NullPointerException e) {
            Log.e(TAG, "PhotoModule set callback failed", e);
        }

        Log.i(TAG, "[END] FaceCaptureMainActivity::onResume()");
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "[BEGIN] FaceCaptureMainActivity::onPause()");
        super.onPause();

        if (mPhotoModule != null)
            mPhotoModule.onPause();
        Log.i(TAG, "[END] FaceCaptureMainActivity::onPause()");
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "[BEGIN] FaceCaptureMainActivity::onStop()");
        super.onStop();

        // 关闭摄像头
        if (mPhotoModule != null)
            mPhotoModule.onStop();
        CameraUtil.sContext = null;
        mPhotoModule = null;

        // 退出摄像头处理线程
        if (mCameraHandlerThread != null) {
            try {
                mCameraHandlerThread.quit();
                mCameraHandlerThread.join();
            } catch (InterruptedException e) {
                LogUtil.e(TAG, "Fail to join CameraHandlerThread", e);
            }
        }
        mCameraHandlerThread = null;

        // 销毁检测逻辑控制器
        if (mFaceCaptureController != null)
            mFaceCaptureController.uninit();
        mFaceCaptureController = null;

        Log.i(TAG, "[END] FaceCaptureMainActivity::onStop()");
    }

    //////////////////////////// INIT ////////////////////////////////

    private FaceCaptureController mFaceCaptureController; // 逻辑控制器
    private Handler mCameraHandler; // 摄像头回调所在的消息队列
    private HandlerThread mCameraHandlerThread; // 摄像头回调所在的消息队列线程

    /**
     * 初始化并打开摄像头
     */
    private void initCamera() {
        Log.i(TAG, "[BEGIN] initCamera");

        // 寻找设备上的后置摄像头
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int expectCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);

            Log.i(TAG, "camera id: " + camIdx + ", facing: " + cameraInfo.facing + ", expect facing: " + expectCameraFacing);
            if (cameraInfo.facing == expectCameraFacing) {
                getIntent().putExtra(CameraUtil.EXTRAS_CAMERA_FACING, camIdx); // 设置需要打开的摄像头ID
                getIntent().putExtra(CameraUtil.MAX_PREVIEW_WIDTH, MAX_PREVIEW_WIDTH); // 设置最大Preview宽度
                getIntent().putExtra(CameraUtil.TARGET_PREVIEW_RATIO, TARGET_PREVIEW_RATIO); // 设置Preview长宽比
            }
        }
        mPhotoModule = new PhotoModule();
        mPhotoModule.init(this, findViewById(getResources().getIdentifier("oliveapp_cameraPreviewView", "id", mPackageName))); // 参考layout XML文件里定义的cameraPreviewView对象
        mPhotoModule.setPlaneMode(false, false); // 取消拍照和对焦功能
        // 打开摄像头预览
        mPhotoModule.onStart();

        // 初始化摄像头处理消息队列
        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        Log.i(TAG, "[END] initCamera");
    }

    /**
     * 初始化界面元素
     */
    private void initViews() {
        // Fullscreen looks better
        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            /*View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);*/

            // Style中已经设置了NoActionBar
//            ActionBar actionBar = getActionBar();
//            actionBar.hide();
        }

        setContentView(R.layout.oliveapp_activity_liveness_detection_main);

        // DEBUG: 调试相关
        mFrameRateText = (TextView) findViewById(getResources().getIdentifier("oliveapp_frame_rate_text", "id", mPackageName));
    }

    // 图片预处理参数
    private ImageProcessParameter mImageProcessParameter;

    /**
     * 设置图片处理参数和活体检测参数
     */
    private void setDetectionParameter() throws Exception {
        /**
         * 注意: 默认参数适合手机，一般情况下不需要修改这些参数。如需修改请联系工程师
         *
         * 设置从preview图片中截取人脸框的位置，调用doDetection前必须调用本函数。
         * @param preRotationDegree　逆时针旋转角度，只允许0 90 180 270，大部分手机应当是90
         *                         以下说明中的帧都是指旋转后的帧 旋转之后的帧建议宽高比例3:4，否则算法结果无法保证
         * @param cropWidthPercent　截取的人脸框宽度占帧宽度的比例
         * @param verticalOffsetPercent　截取的人脸框上边缘到帧上边缘的距离占帧高度的比例，算法真正截取的人脸的尺寸=cropWidthPercent*4/3，再依据这个参数获取需要截取的位置
         * @param shouldFlip 是否左右翻转。一般前置摄像头为false
         */
        mImageProcessParameter = new ImageProcessParameter(false, 1.0f, 0.0f, 90);
    }

    /**
     * 初始化检测逻辑控制器
     * 请先调用setDetectionParameter()设置参数
     */
    private void initControllers() {
        try {
            setDetectionParameter();
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to set parameter...", e);
        }

        // init verification controller
        mFaceCaptureController = new FaceCaptureController(mAccessInfo, FaceCaptureMainActivity.this, mImageProcessParameter, FaceCaptureMainActivity.this, new Handler(Looper.getMainLooper()));
    }

    //////////////// INTERFACE 对外接口 /////////////////
    //////////////// 可以在子类中调用    /////////////////

    /**
     * 调用此函数后活体检测即开始
     * 可以用这个函数来实现类似 用户点击“开始”按钮，倒计时3秒后才启动的功能
     * 如需设置参数，请先调用setDetectionParameter()
     */
    public void startVerification() {
        try {
            if (mFaceCaptureController.getCurrentStep() == VerificationController.STEP_READY) {
                mFaceCaptureController.nextVerificationStep();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "无法开始活体检测...", e);
        }
    }


    ////////////// INITIALIZATION //////////////
    @Override
    public void onInitializeSucc() {
        // 初始化成功
    }

    @Override
    public void onInitializeFail(Throwable e) {
        // 初始化失败
    }


    ////////////// PRESTART 预检步骤 /////////////////

    @Override
    public void onPrestartFrameDetected(PrestartDetectionFrame frame, int remainingTimeMillisecond) {
        /**
         * [预检阶段]
         * 每帧处理完成的回调函数
         * 当前流程设计下不需要处理此事件
         */
        LogUtil.i(TAG, "[BEGIN] onPrestartFrameDetected");

        mFrameRate += 1;
        long currentTimestamp = System.currentTimeMillis();
        if ((currentTimestamp - mLastTimestamp) > 1000) {
            mLastTimestamp = currentTimestamp;
            mFrameRateText.setText("FrameRate: " + mFrameRate + " FPS");
            mFrameRate = 0;
        }

        LogUtil.i(TAG, "[END] onPrestartFrameDetected");
    }


    @Override
    public void onPrestartSuccess(LivenessDetectionFrames livenessDetectionFrames) {
        /**
         * [预检阶段]
         * 检测到符合质量的人脸，认为可以开始活体检测的回调
         */
        LogUtil.i(TAG, "[END] onPrestartSuccess");
    }

    @Override
    public void onPrestartFail(int result) {
        LogUtil.i(TAG, "[END] onPrestartFail");
    }

    /////////////////// FOR DEBUG //////////////////////
    private TextView mFrameRateText;
    private long mLastTimestamp = System.currentTimeMillis();
    private int mFrameRate = 0;
    private static int classObjectCount = 0;

    private void increaseClassObjectCount() {
        // DEBUG: 检测是否有Activity泄漏
        classObjectCount++;
        Log.i(TAG, "FaceCaptureMainActivity classObjectCount onCreate: " + classObjectCount);

        // 预期现象是classObjectCount会在1~2之间抖动，如果classObjectCount一直在增长，很可能有内存泄漏
        if (classObjectCount == 10) {
            System.gc();
        }
        Assert.assertTrue(classObjectCount < 10);
    }

    @Override
    public void finalize() {
        try {
            super.finalize();
        } catch (Throwable e) {
            LogUtil.e(TAG, "无法完成finalize...", e);
        }
        // DEBUG: 检测是否有Activity泄漏。与increaseClassObjectCount对应
        classObjectCount--;
        Log.i(TAG, "FaceCaptureMainActivity classObjectCount finalize: " + classObjectCount);
    }

}
