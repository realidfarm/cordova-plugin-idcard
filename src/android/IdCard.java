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
            callbackContext.success(id2Result[0]);
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
				id2Result[0] = "读卡上电失败";
			}else{
				id2Result=id2Handle.readCardInfo();
				if(id2Result[0].equalsIgnoreCase("0")){
					id2Result=id2Handle.readCardInfo();
				}else{
					id2Result[0] = "读卡失败";
				}
			}
	}
	
}