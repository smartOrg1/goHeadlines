package com.cqyw.goheadlines;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.cqyw.goheadlines.camera.CameraHelper;
import com.cqyw.goheadlines.camera.ScrnOrientDetector;
import com.cqyw.goheadlines.config.Constant;
import com.cqyw.goheadlines.util.Logger;
import com.cqyw.goheadlines.widget.CircleImageView;
import com.cqyw.goheadlines.widget.horizonListView.*;
import com.cqyw.goheadlines.widget.picture.CropImageActivity;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Kairong on 2015/9/20.
 * mail:wangkrhust@gmail.com
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback,View.OnClickListener{

    private CameraHelper cameraHelper;
    private SurfaceView surface;
    private int scrnOrient = 90;                 // 当前屏幕朝向
    private int picTakenScrnOrient = 0;          // 拍照时的手机屏幕朝向

    private ImageView focus_view;                // 显示对焦光标
    private ImageView cover;                     // 封面图片
    private ImageView flash_light;               // 闪光灯
    private CircleImageView expand;              // 扩展按钮
    private ProgressDialog progressDialog;
    /*停止保存照片线程*/
    private boolean ifStopCPThread = false;
    /*显示封面选项菜单*/
    private boolean ifCoverListShown = true;

    /*屏幕方向检测器，用于监测屏幕的旋转*/
    private ScrnOrientDetector scrnOrientDetector;

    private final static int STOP_PROGRESS = 3238;
    public final static int SAVE_PICTURE_DONE = 3239;

    //手机屏幕的旋转方向
    /*水平方向*/
    public static final int ORIENTATION_LAND = 0;
    /*竖直方向*/
    public static final int ORIENTATION_PORTAIT = 90;
    /*反方向水平方向*/
    public static final int ORIENTATION_REV_LAND = 180;
    /*反方向竖直方向*/
    public static final int ORIENTATION_REV_PORTRAIT = 270;

    enum ANIM_TYPE{SCALE,HIDE};

    /*拍照遮幅高度*/
    private int barrier_height = 0;
    /*当前选择的封面*/
    private int curCoverIndex = 0;

    private String TAG = "MainActivity";
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        initCameraHelper();
        // 初始化视图
        initViews();
    }

    // 初始化cameraHelper
    private void initCameraHelper(){
        SurfaceHolder holder;
        surface = (SurfaceView)findViewById(R.id.surfaceview);
        holder = surface.getHolder();   //获得句柄
        holder.addCallback(this);       //添加回调
        // surfaceview不维护自己的缓冲区，等待屏幕渲染引擎将内容推送到用户面前
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        cameraHelper = new CameraHelper(this,holder,handler);
    }
    // 初始化视图资源
    private void initViews(){
        // 设置控件资源ID
        HorizontalListView coverList = (HorizontalListView)findViewById(R.id.cover_icon_list);
        LinearLayout previewing_barrier = (LinearLayout)findViewById(R.id.barrier);

        focus_view = (ImageView)findViewById(R.id.focus_view);
        cover = (ImageView)findViewById(R.id.cover);
        expand = (CircleImageView)findViewById(R.id.expand_button);
        flash_light = (ImageView)findViewById(R.id.flash_light);

        // 设置监听
        expand.setOnClickListener(this);
        findViewById(R.id.shutter).setOnClickListener(this);
        findViewById(R.id.switch_camera).setOnClickListener(this);
        findViewById(R.id.flash_light).setOnClickListener(this);


        int camera_top_bar_height = getResources().getDimensionPixelSize(R.dimen.camera_top_bar_height);
        int barrier_margin_top = Constant.picHeight + camera_top_bar_height;
        // 初始化遮幅高度
        barrier_height = Constant.displayHeight - barrier_margin_top;
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(Constant.displayWidth,barrier_height);
        View v = new View(this);
        previewing_barrier.addView(v, lp);
        // 设置封面
        cover.setImageResource(Constant.coverResIds[0]);

        // 设置“点击聚焦”和拍照模式菜单弹回
        surface.setOnTouchListener(onTouchListener);

        String[] coverTitles = getResources().getStringArray(R.array.cover_title);
        HorizontalListViewAdapter coverListAdater = new HorizontalListViewAdapter(this,Constant.coverIcnIds,coverTitles);
        coverList.setAdapter(coverListAdater);
        coverList.setOnItemClickListener(onItemClickListener_coverList);

        // 闪光灯图标的显示
        if(!Constant.hasFlashLight){
            cameraHelper.flashLightMode = CameraHelper.FLIGHT_NONE;
            flash_light.setVisibility(View.GONE);
        }else {
            // 读取闪光灯默认选项
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            int defFLightMode = sharedPreferences.getInt(Constant.defFlashLightKey,CameraHelper.FLIGHT_OFF);
            cameraHelper.flashLightMode = defFLightMode;
            setFlashLightView(defFLightMode);
        }

    }
    private AdapterView.OnItemClickListener onItemClickListener_coverList = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(position != curCoverIndex){
                cover.setImageResource(Constant.coverResIds[position]);
                curCoverIndex = position;
            }
        }
    };
    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Rect surfaceRect = new Rect();
            view.getDrawingRect(surfaceRect);
            int focus_view_size = getResources().getDimensionPixelSize(R.dimen.focus_view_size);
            int photo_bar_height = getResources().getDimensionPixelSize(R.dimen.camera_top_bar_height)+Math.round(focus_view_size/2) ;
            int bottom_bar_height = getResources().getDimensionPixelSize(R.dimen.camera_bottom_bar_height)+Math.round(focus_view_size/2);
            surfaceRect.top += photo_bar_height;
            surfaceRect.bottom -= bottom_bar_height;
            surfaceRect.left += Math.round(focus_view_size/2);
            surfaceRect.right -= Math.round(focus_view_size/2);
            float m_X = motionEvent.getX(0);
            float m_Y = motionEvent.getY(0);
            if (cameraHelper.isPreviewing
                    && cameraHelper.camera_position == Camera.CameraInfo.CAMERA_FACING_BACK
                    && surfaceRect.contains((int) m_X, (int) m_Y)) {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(focus_view.getLayoutParams());
                // 将focus_view中心移动到点击的地方
                lp.setMargins((int) (m_X - focus_view_size/2), (int) (m_Y - focus_view_size/2), 0, 0);
                focus_view.setLayoutParams(lp);
                cameraHelper.autoFocus(false,false);
            }
            return false;
        }
    };

    public void startFocusViewAnimation(final ANIM_TYPE type){
        Animation.AnimationListener AniListener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if(type == ANIM_TYPE.HIDE){
                    focus_view.setVisibility(View.INVISIBLE);
                }
                focus_view.clearAnimation();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        if(type == ANIM_TYPE.SCALE) {
            ScaleAnimation scaleAnimation = new ScaleAnimation(2f, 1f, 2f, 1f, Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF,0.5f);
            scaleAnimation.setDuration(1000);
            scaleAnimation.setAnimationListener(AniListener);
            focus_view.startAnimation(scaleAnimation);
        }else if(type == ANIM_TYPE.HIDE){
            AlphaAnimation alphaAnimation = new AlphaAnimation(1f,1f);
            alphaAnimation.setDuration(1000);
            alphaAnimation.setAnimationListener(AniListener);
            focus_view.startAnimation(alphaAnimation);
        }
    }

    /**
     * 为了防止系统gc时发生内存泄露
     * 自定义的一个Handler静态类
     */
    static class viCmHandler extends Handler {
        WeakReference<MainActivity> mActivity;
        viCmHandler(MainActivity activity){
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity theActivity = mActivity.get();
            switch (msg.what) {
                case CameraHelper.MSG_FOCUSING:
                    theActivity.focus_view.setImageResource(R.mipmap.focus_icn);
                    theActivity.focus_view.setVisibility(View.VISIBLE);
                    theActivity.startFocusViewAnimation(ANIM_TYPE.SCALE);
                    break;
                case CameraHelper.MSG_FOCUSED:
                    if (theActivity.cameraHelper.camera_position == Camera.CameraInfo.CAMERA_FACING_BACK
                            &&theActivity.cameraHelper.isPreviewing) {
                        theActivity.focus_view.setImageResource(R.mipmap.focus_success_icn);
                        theActivity.focus_view.setVisibility(View.VISIBLE);
                        theActivity.startFocusViewAnimation(ANIM_TYPE.HIDE);
                    }
                    break;
                case CameraHelper.MSG_FOCUS_FAILED:
                    if (theActivity.cameraHelper.camera_position == Camera.CameraInfo.CAMERA_FACING_BACK
                            &&theActivity.cameraHelper.isPreviewing) {
                        theActivity.focus_view.setImageResource(R.mipmap.focus_failed_icn);
                        theActivity.focus_view.setVisibility(View.VISIBLE);
                        theActivity.startFocusViewAnimation(ANIM_TYPE.HIDE);
                    }
                    break;
                case STOP_PROGRESS:
                    if(theActivity.progressDialog!=null) {
                        theActivity.progressDialog.dismiss();
                    }
                    break;
                case SAVE_PICTURE_DONE:
                    if(theActivity.progressDialog!=null) {
                        theActivity.progressDialog.dismiss();
                    }
                    Bundle childMsgBundle = msg.getData();
                    String savedPath = childMsgBundle.getString(Constant.childMsgKey);
                    Logger.d(theActivity.TAG, "send file:" + savedPath);

                    if(savedPath==null) {
                        Toast.makeText(theActivity,"保存图片出错!",Toast.LENGTH_SHORT).show();
                        break;
                    }
                    Intent intent = new Intent(theActivity,CropImageActivity.class);
                    intent.putExtra(Constant.IMAGE_URI,Uri.fromFile(new File(savedPath)));
                    intent.putExtra(Constant.COVER_INDEX, theActivity.curCoverIndex);
                    theActivity.startActivity(intent);
                    break;
                case CameraHelper.MSG_TAKE_PICTURE:
                    theActivity.takePhoto();
                    break;
            }
            super.handleMessage(msg);
        }
    }
    private viCmHandler handler = new viCmHandler(this);
    // 设置一个屏幕旋转监听器--发生屏幕旋转就重新检测
    ScrnOrientDetector.OnSrcnListener onSrcnListener = new ScrnOrientDetector.OnSrcnListener() {
        @Override
        public void onSrcnRoate(int Orientation) {
            int last_scrnOrient = scrnOrient;
            if (Orientation>45&&Orientation<135) {
                scrnOrient = ORIENTATION_REV_LAND;
            }else if (Orientation>135&&Orientation<225){
                scrnOrient = ORIENTATION_REV_PORTRAIT;
            }else if (Orientation>225&&Orientation<315){
                scrnOrient = ORIENTATION_LAND;
            }else if ((Orientation>315&&Orientation<360)||(Orientation>=0&&Orientation<45)){
                scrnOrient = ORIENTATION_PORTAIT;
            }
        }
    };

    // 全局控件点击事件监听
    public void onClick(View v) {

        switch (v.getId())
        {
            case R.id.switch_camera:
                // 切换前后摄像头
                cameraHelper.switchCamera();
                // 写入用户最近选择
                SharedPreferences lastCamPos = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = lastCamPos.edit();
                editor.putInt(Constant.defCamPosKey,cameraHelper.camera_position);
                editor.apply();
                if(cameraHelper.camera_position==Camera.CameraInfo.CAMERA_FACING_BACK){
                    flash_light.setVisibility(Constant.hasFlashLight?View.VISIBLE:View.GONE);
                    cameraHelper.autoFocus(true,false);
                }else{
                    flash_light.setVisibility(View.GONE);
                    focus_view.setVisibility(View.GONE);
                }
                break;
            case R.id.shutter:
                // 拍照
                takePhoto();
                break;
            case R.id.flash_light:
                // 闪光灯
                switchFlashLightMode();
                break;
            case R.id.expand_button:
                // 显示封面选项
                showCoverList();
                break;
        }
    }
    /**
     * 显示/隐藏 封面选项面板
     */
    private void showCoverList(){
        final RelativeLayout cover_icn_rl = (RelativeLayout)findViewById(R.id.cover_icn_rl);
        if(ifCoverListShown){
            expand.setImageResource(R.mipmap.expand_icn);
            cover_icn_rl.setVisibility(View.INVISIBLE);
            ifCoverListShown = false;
        }else{
            expand.setImageResource(R.mipmap.collapse_icn);
            cover_icn_rl.setVisibility(View.VISIBLE);
            ifCoverListShown = true;
        }
    }
    /**
     * 切换闪光灯状态
     */
    public void switchFlashLightMode(){
        switch (cameraHelper.flashLightMode){
            case CameraHelper.FLIGHT_OFF:
                cameraHelper.switchFlashLightMode();
                setFlashLightView(CameraHelper.FLIGHT_ON);
                break;
            case CameraHelper.FLIGHT_ON:
                cameraHelper.switchFlashLightMode();
                setFlashLightView(CameraHelper.FLIGHT_AUTO);
                break;
            case CameraHelper.FLIGHT_AUTO:
                cameraHelper.switchFlashLightMode();
                setFlashLightView(CameraHelper.FLIGHT_OFF);
                break;
            default:
                break;
        }
    }
    /**
     * 设置闪光灯状态
     */
    private void setFlashLightView(int mode){
        switch(mode){
            case CameraHelper.FLIGHT_OFF:
                flash_light.setImageResource(R.mipmap.flash_light_icn_disabled);
                break;
            case CameraHelper.FLIGHT_ON:
                flash_light.setImageResource(R.mipmap.flash_light_icn_enable);
                break;
            case CameraHelper.FLIGHT_AUTO:
                flash_light.setImageResource(R.mipmap.flash_light_icn_auto);
                break;
            default:
                break;
        }
        // 写入用户闪光灯首选项
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(Constant.defFlashLightKey,mode);
        editor.apply();
    }
    /**
     * 得到裁剪宽高比选项菜单内容
     * @param vlist_image:图片资源id
     * @param vlist_text:描述字符串
     * @return
     */
    private List<Map<String,Object>> getData(int[] vlist_image,String[] vlist_text){
        List<Map<String,Object>> list = new ArrayList<Map<String,Object>>();

        Map<String,Object> map = null;
        for(int i = 0;i < vlist_image.length;i++){
            map = new HashMap<String,Object>();
            map.put("vlist_image",vlist_image[i]);
            map.put("vlist_text",vlist_text[i]);
            list.add(map);
        }

        return list;

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder,int format, int width, int height){

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 读取默认摄像头选项
        SharedPreferences cameraPos = PreferenceManager.getDefaultSharedPreferences(this);
        int defCamPos = cameraPos.getInt(Constant.defCamPosKey,Camera.CameraInfo.CAMERA_FACING_FRONT);
        if(defCamPos == Camera.CameraInfo.CAMERA_FACING_FRONT) flash_light.setVisibility(View.GONE);
        cameraHelper.open(defCamPos);

        scrnOrientDetector = new ScrnOrientDetector(this);

        scrnOrientDetector.registerOnShakeListener(onSrcnListener);
        // 启动监听
        scrnOrientDetector.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 置预览回调为空，再关闭预览
        cameraHelper.stop();
        scrnOrientDetector.stop();
        surface = null;
    }

    /**
     * 根据屏幕方向对按钮进行旋转
     */
    private void Btns_Rotate(){
        // 布局默认为竖屏布局方式
    }
    /**
     * 带自动对焦功能的拍照
     */
    private void takePhoto(){
        if(cameraHelper.camera_position == Camera.CameraInfo.CAMERA_FACING_BACK) {
            picTakenScrnOrient = scrnOrient;
            if (!cameraHelper.focusing) {
                // 当前为对焦完成状态则快速拍照
                if(cameraHelper.focuseState)
                    takePhotoThread();
                else{ // 否则自动对焦一次，等到对焦完成，再拍照
                    cameraHelper.autoFocus(true,true);
                }
            }
        }else if(cameraHelper.camera_position == Camera.CameraInfo.CAMERA_FACING_FRONT){
            picTakenScrnOrient = scrnOrient;
            takePhotoThread();
        }
    }
    private void takePhotoThread(){
        progressDialog = ProgressDialog.show(this, null, "正在处理...", true, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                cameraHelper.takePhoto(picTakenScrnOrient, barrier_height);
                while (!ifStopCPThread) {
                    if (cameraHelper.save_photo_state == CameraHelper.SAVED_PHOTO) {
                        break;
                    } else if (cameraHelper.save_photo_state == CameraHelper.SAVED_ERROR) {//保存发生错误
                        handler.sendEmptyMessage(STOP_PROGRESS);
                        cameraHelper.restartPreview();
                        break;
                    }
                }
            }
        }).start();
    }

    private void setScreen(){
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        lp.screenBrightness = 1;
        this.getWindow().setAttributes(lp);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode!=RESULT_OK){
            Toast.makeText(getApplicationContext(),"没有选择任何图片!",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        // 取消注册监听器
        if(scrnOrientDetector!=null) {
            scrnOrientDetector.stop();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if(scrnOrientDetector!=null) {
            scrnOrientDetector.registerOnShakeListener(onSrcnListener);
            scrnOrientDetector.start();
        }
        setScreen();
        super.onResume();
    }


    //无意中按返回键时要释放内存
    @Override
    public void onBackPressed() {
        this.finish();
        System.gc();

    }

    @Override
    public void finish() {
        // 停止等待动画
        handler.sendEmptyMessage(STOP_PROGRESS);
        ifStopCPThread = true;
        super.finish();
    }
}