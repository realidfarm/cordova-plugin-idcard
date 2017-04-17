package com.realidfarm.idcard;

import java.io.File;
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

	ID2CardInterface id2Handle=null;
	String id2Result[]=null;
	public String TAG="ss_500";
	private int openRet=0,closeRet=0;

	private long startTime,endTime;
	
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Activity activity = this.cordova.getActivity();
        if (action.equals("open")) {
            startInit();
			callbackContext.success("设备已开启");
        }else if (action.equals("read")) {
            readCard();
            callbackContext.success(id2Result);
        }else if (action.equals("close")) {
			closeRet=id2Handle.closeReadCard();
			if(closeRet!=1){
				callbackContext.success("读卡下电失败");
			}else{
				callbackContext.success("设备已关闭");
			}
        }
        return false;
    }
	
	private void  startInit() {
		id2Handle=new ID2CardInterface();
		HandlerThread loadHandlerThread = new HandlerThread("LoadThread");
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
		if (ssF == null) {
			ssF = new SSFingerInterfaceImp(MainActivity.this);
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
	
	/** 
	* @Title: readCard 
	* @author xc 
	* @Description: TODO(读二代证) 
	* @param:  
	* @return: void 
	* @throws 
	*/ 
	private void readCard(){
			if(openRet!=1){
				myToast("读卡上电失败");
			}else{
				id2Result=id2Handle.readCardInfo();
				if(id2Result[0].equalsIgnoreCase("0")){
					id2Result=id2Handle.readCardInfo();
				}else{
					id2Result="读卡失败";
				}
			}
	}
	
}