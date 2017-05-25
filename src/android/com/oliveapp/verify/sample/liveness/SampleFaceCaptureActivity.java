package com.oliveapp.verify.sample.liveness;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.oliveapp.face.liboffline_face_verification.FaceVerifier;
import com.oliveapp.face.liboffline_face_verification.nativecode.LivenessPackage;
import com.oliveapp.face.livenessdetectorsdk.livenessdetector.datatype.LivenessDetectionFrames;
import com.oliveapp.verify.sample.liveness.view_controller.FaceCaptureMainActivity;
import com.oliveapp.verify.sample.service.FaceService;
import com.oliveapp.verify.sample.service.IRemoteService;
import com.oliveapp.verify.sample.service.ServiceFactory;

/**
 * 捕捉人像界面
 */
public class SampleFaceCaptureActivity extends FaceCaptureMainActivity {
    public static final String TAG = SampleFaceCaptureActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 如果有设置全局包名的需要, 在这里进行设置
//        PackageNameManager.setPackageName();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    ////////////// INITIALIZATION //////////////
    @Override
    public void onInitializeSucc() {
        super.onInitializeSucc();
        super.startVerification(); // 开始人脸捕获
    }

    @Override
    public void onInitializeFail(Throwable e) {
        super.onInitializeFail(e);
        Log.e(TAG, "无法初始化活体检测...", e);
        Toast.makeText(this, "无法初始化活体检测", Toast.LENGTH_LONG).show();

        Intent it = new Intent();
        it.putExtra("is_success",false);
        setResult(Activity.RESULT_OK, it);
        finish();
    }

    ////////////// LIVENESS DETECTION /////////////////

    /**
     * 将原始数据解析成人像数据
     * @param packageData 原始数据
     * @return 人像数据
     */
    private byte[] getFrameFromPackageData(byte[] packageData)
    {
        FaceVerifier verifier = ServiceFactory.getInstance().getFaceVerifier();
        LivenessPackage pack = new LivenessPackage();
        int rtn = verifier.parsePackageData(packageData, pack); // 解析数据
        if (rtn != 0) return null;

        byte[] data = verifier.getImageInPackage(pack, 0); // 获取人像图片数据
        pack.delete();
        return data;
    }

    /*******************************************************************
     *
     * 人像捕获核心代码
     * 成功捕获人像后，会调用此方法
     * 在此方法中可处理图像数据，例如将图像数据传递给下一个Activity
     *
     * @param detectionFrames 捕获到的帧数据
     *
     *******************************************************************/
    @Override
    public void onPrestartSuccess(final LivenessDetectionFrames detectionFrames) {
        super.onPrestartSuccess(detectionFrames);

        Intent it = new Intent();
        Log.e(TAG, "package_content 长度："+detectionFrames.verificationPackage.length);
        it.putExtra("package_content", detectionFrames.verificationPackage);
        it.putExtra("is_success", true);
        setResult(Activity.RESULT_OK, it);
        finish();

    }

    @Override
    public void onPrestartFail(int result) {
        super.onPrestartFail(result);

        //finish();
        Log.e(TAG, "onPrestartFail 捕获人脸失败");
        Toast.makeText(this, "onPrestartFail 捕获人脸失败", Toast.LENGTH_LONG).show();

        Intent it = new Intent();
        it.putExtra("is_success",false);
        setResult(Activity.RESULT_OK, it);
        finish();
    }

    private void handleLivenessFinish(Intent i) {
        startActivity(i);
        finish();
    }
}
