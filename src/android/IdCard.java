package com.realidfarm.idcard;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.sdses.fingerJar.SSFingerInterfaceImp;
import com.sdses.id2CardInterface.ID2CardInterface;
import com.sdses.tool.SSUtil;

public class IdCard extends CordovaPlugin{

	ID2CardInterface id2Handle = null;
	String id2Result[] = null;
	public String TAG="ss_500";
	private int openRet=0,closeRet=0;
	
	private ImageView iv_fp;
	private SSFingerInterfaceImp ssF=null;
	private ProgressDialog pd=null;
	private String fingerInfo1,fingerInfo2;
	private int iFpCompareThreshold=40;
	private TextView tv_fpHint;
	private boolean openFp=false;
	byte[] fingerInfo = new byte[93238];
	
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Activity activity = this.cordova.getActivity();
        if (action.equals("open")) {
    		id2Handle = new ID2CardInterface();
    		openRet=id2Handle.openReadCard();
			callbackContext.success("设备已开启");
        }else if (action.equals("read")) {
        	if(openRet!=1){
				id2Result[0] = "读卡上电失败";
			}else{
				id2Result = id2Handle.readCardInfo();
				if(id2Result[0].equalsIgnoreCase("0")){
					byte[] bid2Result = SSUtil.hexStringToByte(id2Result[12]);
					id2Result[12] = Base64.encodeToString(bid2Result, 0);
		            callbackContext.success(Arrays.toString(id2Result));
				}else{
		            callbackContext.success("读卡失败");
				}
			}
        }else if (action.equals("close")) {
			closeRet=id2Handle.closeReadCard();
			if(closeRet!=1){
				callbackContext.success("读卡下电失败");
			}else{
				callbackContext.success("设备已关闭");
			}
        }else if (action.equals("ssFopen")) {
        	startInit();
			callbackContext.success("上电成功");
        }else if (action.equals("ssFget")) {
			fingerInfo1 = ssF.getFingerInfo(1, fingerInfo);
			if(fingerInfo1!=null&&!fingerInfo1.equals("")){
				callbackContext.success(fingerInfo1);
			}else{
				callbackContext.success("请采集指纹");
			}
        }
        return false;
    }
    
	private void  startInit() {
		HandlerThread 	loadHandlerThread = new HandlerThread("LoadThread");
		loadHandlerThread.start();
		Handler loadHandler = new Handler(loadHandlerThread.getLooper());
		loadHandler.post(loadRunnable);
	}
	
	Runnable loadRunnable=new Runnable() {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(Init()){
				Log.e(TAG, "上电成功");
			}
		}
	};
	
	public boolean Init() {
		// TODO Auto-generated method stub
		if (ssF == null) {
			ssF = new SSFingerInterfaceImp(null);
		}
		int power_res = ssF.f_powerOn();
		if (power_res == 0) {
			int fpCon = -1;
			for (int i = 0; i < 5; i++) {
				fpCon = ssF.SS_USBConnect();
				SystemClock.sleep(1000);
				Log.e(TAG, "循环中");
				if (fpCon == 0) {
					 Log.e(TAG, "指纹上电成功");
					break;
				} else if (i == 4) {
					 Log.e(TAG, "指纹上电失败");
					break;
				}
			}
			if (fpCon == 0) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	Runnable fpRun=new Runnable() {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			//大约需要3s时间 5s连接不上 可当做失败
			Log.e(TAG, "btn_openFP5");
			for(int i=0;i<5;i++){
				int fpCon=ssF.SS_USBConnect();
				SystemClock.sleep(1000);
				if(fpCon==0){
					Log.e(TAG, "指纹上电成功");
					break;
				}else if(i==4){
					Log.e(TAG, "指纹上电失败");
					break;
				}
			}
		}
	};
	
}