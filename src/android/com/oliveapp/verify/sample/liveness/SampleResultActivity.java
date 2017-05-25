package com.oliveapp.verify.sample.liveness;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.oliveapp.face.liboffline_face_verification.nativecode.FaceFeature;
import com.oliveapp.verify.sample.model.FaceVerifyResult;
import com.oliveapp.verify.sample.service.FaceService;
import com.oliveapp.verify.sample.service.IRemoteService;
import com.sdses.beanmy.ID2Data;
import com.sdses.readcardservice.IReadCardService;
import com.sdses.utils.AppConstant;
import com.sdses.utils.Utils;
import com.sdses.values.ClientVars;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ionicframework.xmdemo430114.R;

/**************************************************
 * 芯片照比与捕获人像对界面
 * 分为以下步骤：
 * 1. 刷身份证获取芯片照,提取芯片照特征（异步任务）
 * 2. 获取捕获到的人像正脸
 * 3. 提取捕获到的人像正脸特征（异步任务）
 * 4. 比对照片特征（异步任务）
 * 5. 显示比对结果
 **************************************************/
public class SampleResultActivity extends Activity {

    public static final String TAG = SampleResultActivity.class.getSimpleName();
    // 控件
    private TextView mReaderInfoTextView; // 显示读卡器状态
    private TextView mCompareResultTextView; // 显示“是不是同一个人”
    private TextView mCompareScoreResultTextView; // 显示相似度分数
    // 比对图片
    private ImageView mPhotoImageView; // 拍摄得到的照片
    private ImageView mIdCardImageView; // 身份证照片
    // 声音相关变量
    private SoundPool m_soundPool;
    private int m_nReadIDCardSuccessSoundID;//读卡成功
    private int m_nReadIDCardFailureSoundID;//读卡失败

    private int m_nStartcapturefaceSoundID;//开始捕捉人脸
    private int m_nStartcomparefaceSoundID;//开始人脸比对
    private int m_nSamepersonSoundID;      //是同一个人
    private int m_nNotsamepersonSoundID;   //不是同一个人

    // 标识
    private boolean bIsCaptureSuccess = false; // 标识照片是否成功获取
    // 处理图像相关
    private Future<byte[]> mPhotoFeature;  // extracted photo feature
    private Future<byte[]> mIdCardFeature; // extracted idcard feature

    // 特征数据相关
    private byte[] idCardFeature;//证件照片特征数据
    private byte[] photoFeature;//捕获照片特征数据
    // 捕捉图片数据
    private byte[] packageData;
    private byte[] imageData;
    // 服务组件
    private IRemoteService mIRemoteService;
    // running thread
    private Handler mHandler = new Handler();
    private ExecutorService executor = Executors.newCachedThreadPool();

    //二代证
    byte[] ID2Bytes = new byte[1280];
    //身份证指纹1
    byte[] ID2FpData1 = new byte[512];
    //身份证指纹2
    byte[] ID2FpData2 = new byte[512];
    byte[] bFP= new byte[1024];
    /**
     * 二代证读卡服务接收器
     */
    private ID2DataReceiver mID2DataReceiver;
    //读卡服务绑定成功
    private boolean mBindReadCardServiceIsOK = false;
    private boolean mBlnOpenReadCard = false;
    //读卡服务
    private IReadCardService mIReadCardService = null;
    /**
     * 是否已发送获取sam模块号消息
     */
    private boolean   mBlnHaveSendGetSAMMsg = false;
    /**
     * 清二代证信息显示消息号
     */
    public static final int MSG_CLEAR_DISP = 41;
    /**
     * 关闭二代证读卡功能消息号
     */
    public static final int MSG_ID2READER_CLOSE = 42;
    /**
     * 开启二代证读卡功能消息号
     */
    public static final int MSG_ID2READER_OPEN = 43;
    /**
     * 获取sam模块号消息
     */
    public static final int MSG_GET_SAMID = 44;
    /**
     * 读卡功能开启失败
     */
    public static final int MSG_ID2READER_OPEN_ERROR = 45;
    private int  mIntGetSAMIDCount = 0;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.w(TAG, "onCreate");
        mContext=getApplicationContext();
        iniViews();
        //绑定人脸服务
        Intent intent = new Intent(this, FaceService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        loadPromptSoundFile();

        //注册接收器，从二代证读卡服务接收二代证原始数据及其他消息
        mID2DataReceiver = new ID2DataReceiver();
        IntentFilter filterID2 = new IntentFilter();// 创建IntentFilter对象
        // 注册一个广播，用于接收Activity传送过来的命令，控制Service的行为，如：发送数据，停止服务等
        filterID2.addAction(ClientVars.receivefromserver);
        //注册读卡服务消息号
        filterID2.addAction("com.sdses.readercontrol");
        registerReceiver(mID2DataReceiver, filterID2);
        bindReadCardService();

    }

    @Override
    protected void onResume() {
        Log.w(TAG, "onResume");

        // 启动读卡服务
        IntentFilter filterID2 = new IntentFilter();// 创建IntentFilter对象
        // 注册一个广播，用于接收Activity传送过来的命令，控制Service的行为，如：发送数据，停止服务等
        filterID2.addAction(ClientVars.receivefromserver);
        //注册读卡服务消息号
        filterID2.addAction("com.sdses.readercontrol");
        registerReceiver(mID2DataReceiver, filterID2);
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.w(TAG, "onStop");
        //停止读卡服务
        unregisterReceiver(mID2DataReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.w(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        Log.w(TAG, "onDestroy");

        //关闭FaceService
        if (mConnection != null) {
            unbindService(mConnection);
        }

        //关闭读卡服务
        if(mBindReadCardServiceIsOK){
            unbindService(readCardSerConn);
        }
        super.onDestroy();
    }

    private void iniViews() {
        setContentView(getResources().getIdentifier("oliveapp_activity_sample_result", "layout", getPackageName()));

        mReaderInfoTextView = (TextView) findViewById(getResources().getIdentifier("oliveapp_readerStatusTextView", "id", getPackageName()));
        mCompareResultTextView = (TextView) findViewById(getResources().getIdentifier("oliveapp_compareResultTextView", "id", getPackageName()));
        mCompareScoreResultTextView = (TextView) findViewById(getResources().getIdentifier("oliveapp_compareScoreResultTextView", "id", getPackageName()));
        mPhotoImageView = (ImageView) findViewById(getResources().getIdentifier("oliveapp_photoImageView", "id", getPackageName()));
        mIdCardImageView = (ImageView) findViewById(getResources().getIdentifier("oliveapp_idCardImageView", "id", getPackageName()));

        //设置照片控件响应
        setIdCardPhotoListener();
        setPhotoListener();

    }

    /**
     * 显示比对结果
     *
     * @param result 比对结果
     */
    private void showCompareResult(FaceVerifyResult result) {

        /************************************************
         *
         * # 显示比对结果
         *   result.isSamePerson()方法来得到是否为同一个人，true=是同一个人,false=不是同一个人
         *   result.getSimilarity()方法来得到相似度（百分制）大于66算同一个人
         *
         ************************************************/
        if (result.isSamePerson()) {
            mCompareResultTextView.setText("是同一个人");
            Utils.DisplayToast(SampleResultActivity.this, "是同一个人!", 1);
            PlayPromptSoundFile(m_nSamepersonSoundID);
        }
        else {
            mCompareResultTextView.setText("不是同一个人");
            Utils.DisplayToast(SampleResultActivity.this, "不是同一个人!", 5);
            PlayPromptSoundFile(m_nNotsamepersonSoundID);
        }

        mCompareScoreResultTextView.setText(String.format("相似度分数：%.2f", result.getSimilarity()) );

    }

    /**
     * 将图片数据显示在控件上
     *
     * @param imageView 图片控件
     * @param picData   图片数据
     */
    private void setImageView(ImageView imageView, byte[] picData) {
        Bitmap bm = BitmapFactory.decodeByteArray(picData, 0, picData.length);
        imageView.setImageBitmap(bm);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }


    // 载入读卡声音
    private void loadPromptSoundFile() {
        m_soundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 5);
        m_nReadIDCardSuccessSoundID = m_soundPool.load(this, R.raw.success, 0);
        m_nReadIDCardFailureSoundID = m_soundPool.load(this, R.raw.error, 0);

        m_nStartcapturefaceSoundID = m_soundPool.load(this, R.raw.startcaptureface, 0);
        m_nStartcomparefaceSoundID = m_soundPool.load(this, R.raw.startcompareface, 0);
        m_nSamepersonSoundID = m_soundPool.load(this, R.raw.sameperson, 0);
        m_nNotsamepersonSoundID = m_soundPool.load(this, R.raw.notsameperson, 0);


    }

    // 读卡成功/失败后的声音
    private void PlayPromptSoundFile(int nSoundID) {
        m_soundPool.play(nSoundID, 1, 1, 0, 0, 1);
    }

    //////////// GET PICTURE FEATURE AND COMPARE 获取特征并比较 ////////////

    // 标识处理的图片类型
    public static final int PHOTO_IMAGE = 0; // 捕获人像
    public static final int IDCARD_IMAGE = 1; // 芯片照片

    private void onFeatureExtractionFailed() {
        Toast.makeText(SampleResultActivity.this, "特征提取失败", Toast.LENGTH_SHORT).show();
    }


    // 连接FaceService
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mIRemoteService = IRemoteService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "service disconnected");
        }
    };

    /**
     * 绑定读卡服务
     */
    public void bindReadCardService(){
        Log.w(TAG, "to bind readcard service ");
        Intent intent = new Intent();//只能成功一次
        intent.setAction(IReadCardService.class.getName());
        Intent intent2 =getExplicitIntent(mContext,intent);
        if(intent2!=null) {
            intent = new Intent(intent2);
        }else{
            myToast("绑定服务不成功，请确认是否安装并启动服务");
            return;
        }

        if (bindService(intent /*new Intent(IReadCardService.class.getName())*/, readCardSerConn,
                Context.BIND_AUTO_CREATE)) {
            if (readCardSerConn != null) {
                mBindReadCardServiceIsOK = true;
            } else {
                mBindReadCardServiceIsOK = false;
            }
        } else {
            myToast("绑定服务不成功，请确认是否安装并启动服务");
        }
    }
    //----------------------------------------------------------------------
    //读卡服务连接
    private ServiceConnection readCardSerConn = new ServiceConnection() {
        // 此方法在系统建立服务连接时调用
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.w(TAG, "readCardSerConn() called");
            mIReadCardService = IReadCardService.Stub.asInterface(service);
            if(mIReadCardService != null){
                try {
                    mIReadCardService.startReadCard();
                    //myToast("正在开启读卡，请稍候");
                    Log.w(TAG, "正在开启读卡，请稍候");

                    if(!mBlnHaveSendGetSAMMsg){
                        Log.w(TAG, "延时三秒后测试sam模块状态");
                        myHandler.sendEmptyMessageDelayed(MSG_GET_SAMID, 3000);
                        mBlnHaveSendGetSAMMsg = true;
                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            } else {

            }
        }

        // 此方法在销毁服务连接时调用
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "on readcard ServiceDisconnected()");

        }
    };

    /**
     * 调用气泡提醒
     * @param info
     */
    private void myToast(String info){
        Toast.makeText(SampleResultActivity.this, info, Toast.LENGTH_SHORT).show();
    }

    //绑定读卡服务用
    public static Intent getExplicitIntent(Context context, Intent implicitIntent) {
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        // Make sure only one match was found
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        // Get component info and create ComponentName
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        // Create a new intent. Use the old one for extras and such reuse
        Intent explicitIntent = new Intent(implicitIntent);
        // Set the component to be explicit
        explicitIntent.setComponent(component);
        return explicitIntent;
    }

    /**
     * 二代证数据处理
     */
    private class ID2DataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // log.info("ID2DataReceiver接收消息" + intent.getAction());
            Bundle bundle = intent.getExtras();
            if(intent.getAction().equals("com.sdses.readercontrol")
                    && (bundle.getInt(ClientVars.command) == ClientVars.protocol_0A)){
                Log.w(TAG, "receive msg "+bundle.getInt("para"));
                switch(bundle.getInt("para")){
                    case 3:
                        Log.w(TAG, "正在读卡...");
                       /* mIdCardImageView.setImageBitmap(null);*/
                        mReaderInfoTextView.setText("正在读卡...");
                        break;
                    case 4:
                        Log.w(TAG, "选卡失败");
                        //setWarningInfo("选卡失败");
                        break;
                    case 5:
                        Log.w(TAG, "读卡失败");
                        //setWarningInfo("读卡失败");
                        break;
                    case 6:
                        try{
                            byte[] temp = bundle.getByteArray("dataSrc");
                            if (temp != null ) {
                                // Log.w(TAG, "^^^^^"+Util.toHexStringWithSpace(temp, 40));
                                Log.w(TAG, "temp 长度: "+temp.length);
                                Arrays.fill(ID2Bytes, (byte) 0x0);
                                System.arraycopy(temp, 0, ID2Bytes, 0, 1280);
                                showID2Info(ID2Bytes);
                                if(bundle.getBoolean(ClientVars.ishaveFpData)){
                                    //指纹信息
                                }
                            } else {
                                PlayPromptSoundFile(m_nReadIDCardFailureSoundID);
                                mReaderInfoTextView.setText("读卡失败");
                            }
                        } catch(Exception e){
                            e.printStackTrace();
                        }
                        break;
                }
            }
        }
    }


    /***
     * 显示二代证信息
     * @param data
     */
    private void showID2Info(byte[] data){
        try{
            ID2Data _id2Data = new ID2Data();
            _id2Data.decode_debug(data);
            _id2Data.rePackage();

            mReaderInfoTextView.setText("读卡成功");
            PlayPromptSoundFile(m_nReadIDCardSuccessSoundID);

            Bitmap bm = BitmapFactory.decodeByteArray(_id2Data.getID2Pic().getHeadFromCard(), 0, 38862);
            mIdCardImageView.setImageBitmap(bm);


            /******************************************************************
             *
             *  刷身份证获得芯片照
             *  则开始提取芯片照片特征
             *
             *****************************************************************/
            Log.e(TAG,"证件照片提取特征开始");
            final byte[] idCardImageData = _id2Data.getID2Pic().getHeadFromCard();
            if (idCardImageData == null) {
                Log.e(TAG, "idCardImageData package data is null");
                return;
           }

            // 开始特征分析
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        idCardFeature = mIRemoteService.extractFeature(idCardImageData, IDCARD_IMAGE);
                        if (idCardFeature == null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    //onFeatureExtractionFailed();
                                    Toast.makeText(SampleResultActivity.this, "证件照片特征提取失败!", Toast.LENGTH_SHORT).show();
                                    mCompareResultTextView.setText("证件照片特征提取失败!");
                                    mCompareScoreResultTextView.setText("");
                                }
                            });
                            return;
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "extracted idcard feature failed", e);
                    }
                }
            });

            //读完卡后自动弹出捕捉人脸界面
            mCompareResultTextView.setText("开始捕捉人脸...");
            mCompareScoreResultTextView.setText("");
            Intent i = new Intent(this, SampleFaceCaptureActivity.class);
            startActivityForResult(i, AppConstant.FACE_CAPTURE);
            PlayPromptSoundFile(m_nStartcapturefaceSoundID);

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String strMsg = "";
        switch (requestCode) {
            case AppConstant.FACE_CAPTURE: // 人脸捕捉完成后，提取特征值开始比对

                if (resultCode != Activity.RESULT_OK) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("未捕获到人像\n请重新拍摄");
                    Log.e(TAG,"onActivityResult resultCode != Activity.RESULT_OK 未捕获到人像,请重新拍摄");
                    return;
                }
                // 是否成功捕获人像
                if(data==null) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("未捕获到人像\n请重新拍摄");
                    Log.e(TAG,"onActivityResult data==null 未捕获到人像,请重新拍摄");
                    return;
                }

                Log.e(TAG,"onActivityResult data!=null 开始解析拍照数据");
                bIsCaptureSuccess = data.getBooleanExtra("is_success", false);
                if (bIsCaptureSuccess) {
                    PlayPromptSoundFile(m_nStartcomparefaceSoundID);
                    mCompareResultTextView.setText("正在比对...");
                    mCompareScoreResultTextView.setText("");
                    packageData = data.getByteArrayExtra("package_content");
                    if (packageData == null) {
                        Log.e(TAG, "AppConstant.FACE_CAPTURE packageData package data is null");
                        mCompareResultTextView.setText("package data is null");
                        return;
                    }

                    try {
                        //获取捕获照片数据
                        imageData = mIRemoteService.getFrameFromPackageData(packageData);
                        if (imageData == null) {
                            Log.e(TAG, "image not parsed is null");
                            mCompareResultTextView.setText("image not parsed is null");
                            return;
                        }
                        //显示捕获的照片
                        setImageView(mPhotoImageView, imageData);
                    } catch (RemoteException e) {
                        Log.e(TAG, "extract image from package failed", e);
                        mCompareResultTextView.setText("extract image from package failed "+e.getMessage());
                        return;
                    }
                    //用线程提取捕获照片特征数据并比对
                    new Thread(facecompareRun).start();

                } else {
                    mCompareResultTextView.setText("未捕获到人像\n请重新拍摄");
                    mCompareScoreResultTextView.setText("");
                    Log.w(TAG,"onActivityResult未捕获到人像,请重新拍摄");
                }
                break;
            case AppConstant.KITKAT_LESS://从图库选择照片
                if (resultCode != Activity.RESULT_OK) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("没有选择照片");
                    return;
                }
                if(data==null) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("没有选择照片");
                    return;
                }

                //从图库选择照片返回
                mCompareResultTextView.setText("本地图库照片");
                mCompareScoreResultTextView.setText("");
                Log.e(TAG, "onActivityResult　KITKAT_LESS 从图库选择照片返回");
                Uri selectedImageLess = data.getData();
                Log.e(TAG, "selectedImageLess=" + selectedImageLess);
                String[] filePathColumnLess = { MediaStore.Images.Media.DATA };

                Cursor cursorLess = getContentResolver().query(selectedImageLess,
                        filePathColumnLess, null, null, null);
                cursorLess.moveToFirst();

                int columnIndexLess = cursorLess.getColumnIndex(filePathColumnLess[0]);
                String picturePathLess = cursorLess.getString(columnIndexLess);
                Log.e(TAG, "picturePathLess=" + picturePathLess);
                cursorLess.close();
                mIdCardImageView.setImageBitmap(BitmapFactory.decodeFile(picturePathLess));

                File file = new File(picturePathLess);
                FileInputStream inputFile;
                try {
                    inputFile = new FileInputStream(file);
                    final byte[] idCardImageDataLess = new byte[(int) file.length()];
                    inputFile.read(idCardImageDataLess);
                    inputFile.close();

                    if (idCardImageDataLess == null) {
                        Log.e(TAG, "idCardImageDataLess package data is null");
                        mCompareResultTextView.setText("读取选择照片失败!");
                        mCompareScoreResultTextView.setText("");
                        return;
                    }
                    // 开始特征分析
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                idCardFeature = mIRemoteService.extractFeature(idCardImageDataLess, IDCARD_IMAGE);
                                if (idCardFeature == null) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(SampleResultActivity.this, "选择的照片特征提取失败!", Toast.LENGTH_SHORT).show();
                                            mCompareResultTextView.setText("选择的照片特征提取失败!");
                                            mCompareScoreResultTextView.setText("");
                                        }
                                    });
                                    return;
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "extracted select photo less feature failed", e);
                            }
                        }
                    });

                  } catch (FileNotFoundException e) {
                    e.printStackTrace();
                  } catch (IOException e) {
                    e.printStackTrace();
                  }

                mCompareResultTextView.setText("请点击人像照片\n开始捕捉人脸...");
                mCompareScoreResultTextView.setText("");
                break;
            case AppConstant.KITKAT_ABOVE://从图库选择照片
                if (resultCode != Activity.RESULT_OK) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("没有选择照片");
                    return;
                }
                if(data==null) {
                    mCompareScoreResultTextView.setText("");
                    mCompareResultTextView.setText("没有选择照片");
                    return;
                }

                //从图库选择照片返回
                mCompareResultTextView.setText("本地图库照片");
                mCompareScoreResultTextView.setText("");
                Log.e(TAG, "onActivityResult　KITKAT_ABOVE 从图库选择照片返回");

                Uri selectedImageAbove = data.getData();
                Log.e(TAG, "selectedImageAbove=" + selectedImageAbove);
                // 先将这个uri转换为path，然后再转换为uri
                String picturePathAbove = Utils.getInstance().getPath(this, selectedImageAbove);
                Log.e(TAG, "picturePathAbove=" + picturePathAbove);
                mIdCardImageView.setImageBitmap(BitmapFactory.decodeFile(picturePathAbove));

                File fileAbove = new File(picturePathAbove);
                FileInputStream inputFileAbove;
                try {
                    inputFileAbove = new FileInputStream(fileAbove);
                    final byte[] idCardImageDataAbove = new byte[(int) fileAbove.length()];
                    inputFileAbove.read(idCardImageDataAbove);
                    inputFileAbove.close();

                    if (idCardImageDataAbove == null) {
                        Log.e(TAG, "idCardImageDataAbove package data is null");
                        mCompareResultTextView.setText("读取选择照片失败!");
                        mCompareScoreResultTextView.setText("");
                        return;
                    }
                    // 开始特征分析
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                idCardFeature = mIRemoteService.extractFeature(idCardImageDataAbove, IDCARD_IMAGE);
                                if (idCardFeature == null) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(SampleResultActivity.this, "选择的照片特征提取失败!", Toast.LENGTH_SHORT).show();
                                            mCompareResultTextView.setText("选择的照片特征提取失败!");
                                            mCompareScoreResultTextView.setText("");
                                        }
                                    });
                                    return;
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "extracted select photo above feature failed", e);
                            }
                        }
                    });

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mCompareResultTextView.setText("请点击人像照片\n开始捕捉人脸...");
                mCompareScoreResultTextView.setText("");
                break;
            default:
                break;
        }
    }


    private Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_SAMID:   //获取SAM模块号
                    if(mIReadCardService != null && mBindReadCardServiceIsOK){
                        try {
                            String strSAM = mIReadCardService.getSAMID();
                            Log.w(TAG, "*** samid is:"+strSAM);
                            if(strSAM==null){
                                if(mIntGetSAMIDCount<4){
                                    myHandler.sendEmptyMessageDelayed(MSG_GET_SAMID, 1500);
                                    mIntGetSAMIDCount++;
                                } else{
                                    mIntGetSAMIDCount = 0;
                                    mReaderInfoTextView.setText("读卡服务启动失败,请重启应用或重启机具");
                                    Log.w(TAG,"读卡服务启动失败,请重启应用或重启机具");
                                }
                            } else{
                                mReaderInfoTextView.setText("开启读卡成功，请放卡");
                                mIntGetSAMIDCount = 0;
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case AppConstant.FACE_SHOW:                   //显示人像
                    break;
                case AppConstant.FACE_EXTRACT_FEATURE_RESULT: //提取人像特征
                    Toast.makeText(SampleResultActivity.this, "人像特征提取失败!", Toast.LENGTH_SHORT).show();
                    mCompareResultTextView.setText("人像特征提取失败!");
                    mCompareScoreResultTextView.setText("");
                    break;
                case AppConstant.FACE_COMPARE:                //人像比对 并显示结果
                    try {
                        photoFeature = mPhotoFeature.get();
                        final FaceVerifyResult result = mIRemoteService.verifyFeature(photoFeature, idCardFeature);
                        showCompareResult(result);
                    } catch (Exception e) {
                        mCompareResultTextView.setText("比对失败!");
                        mCompareScoreResultTextView.setText("");
                        Log.e(TAG,"AppConstant.FACE_COMPARE　比对失败!");
                        Utils.DisplayToast(SampleResultActivity.this, "比对失败!", 5);
                    }
                    break;
                case AppConstant.FACE_EXCEPTION:
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    //点证件照片来选择照片
    private void setIdCardPhotoListener() {
        mIdCardImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mCompareResultTextView.setText("开始选择照片...");
                mCompareScoreResultTextView.setText("");
                Utils.getInstance().selectPicture(SampleResultActivity.this);
            }
        });
    }

    //点人像照片来捕捉人脸
    private void setPhotoListener() {
        mPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                mCompareResultTextView.setText("开始捕捉人脸...");
                mCompareScoreResultTextView.setText("");

                startActivityForResult(new Intent(SampleResultActivity.this,
                        SampleFaceCaptureActivity.class), AppConstant.FACE_CAPTURE);

                PlayPromptSoundFile(m_nStartcapturefaceSoundID);

            }
        });
    }

    //用线程去发起提取捕获照片的特征值
    Runnable facecompareRun=new Runnable() {
        @Override
        public void run() {

            mPhotoFeature = executor.submit(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    byte[] result = mIRemoteService.extractFeatureFromPackage(packageData, 0, PHOTO_IMAGE);
                    if (result == null) {
                        myHandler.sendEmptyMessage(AppConstant.FACE_EXTRACT_FEATURE_RESULT);
                    }
                    if (result != null) {
                        myHandler.sendEmptyMessage(AppConstant.FACE_COMPARE);
                    }
                    return result;
                }
            });

        }
    };




}
