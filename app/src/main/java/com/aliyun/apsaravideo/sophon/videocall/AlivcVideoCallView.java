package com.aliyun.apsaravideo.sophon.videocall;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.alirtc.beacontowner.R;
import com.alivc.rtc.AliRtcAuthInfo;
import com.alivc.rtc.AliRtcEngine;
import com.alivc.rtc.AliRtcRemoteUserInfo;
import com.aliyun.apsaravideo.sophon.bean.ChartUserBean;
import com.aliyun.apsaravideo.sophon.bean.RTCAuthInfo;
import com.aliyun.apsaravideo.sophon.mqtt.MqttServer;
import com.aliyun.apsaravideo.sophon.rtc.RTCBeaconTowerCallback;
import com.aliyun.apsaravideo.sophon.rtc.RTCBeaconTowerImpl;
import com.aliyun.apsaravideo.sophon.utils.DensityUtil;
import com.aliyun.apsaravideo.sophon.utils.FastClickUtil;
import com.aliyun.apsaravideo.sophon.utils.ToastUtils;
import com.aliyun.apsaravideo.sophon.videocall.adapter.BaseRecyclerViewAdapter;
import com.aliyun.apsaravideo.sophon.videocall.adapter.ChartUserAdapter;
import com.aliyun.apsaravideo.sophon.videocall.view.AlivcControlView;
import com.aliyun.apsaravideo.sophon.videocall.view.AlivcTimeTextView;

import org.webrtc.sdk.SophonSurfaceView;

import java.lang.ref.WeakReference;

import static com.alivc.rtc.AliRtcEngine.AliRtcAudioTrack.AliRtcAudioTrackNo;
import static com.alivc.rtc.AliRtcEngine.AliRtcRenderMode.AliRtcRenderModeAuto;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackBoth;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackCamera;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackNo;
import static com.alivc.rtc.AliRtcEngine.AliRtcVideoTrack.AliRtcVideoTrackScreen;
import static com.aliyun.apsaravideo.sophon.utils.ThreadUtils.runOnUiThread;

/**
 * ??????????????????????????????????????????????????????????????????SDK??????1v1???????????????
 */
public class AlivcVideoCallView extends FrameLayout implements RTCBeaconTowerCallback {
    private static final String TAG = "AlivcVideoCallView";
    private static final int HANDLER_HIDE_TASK = 0x0001;
    /**
     * ??????????????????????????????????????????
     */
    private static boolean IMAGE_BUTTON_IS_SHOW_FLAG = true;
    /**
     * ??????????????????????????????
     */
    private static boolean CAN_EXECUTE_SHOW_ANIMATOR = false;
    /**
     * ??????????????????????????????
     */
    private static boolean CAN_EXECUTE_HIDE_ANIMATOR = true;

    /**
     * ??????????????????????????????
     * ????????????
     */
    AlivcControlView alivcControlView;
    /**
     * ????????????????????????Adapter
     */
    private ChartUserAdapter mUserVideoListAdapter;

    /**
     * ????????????????????????
     */
    private ValueAnimator hideValueAnimator;
    /**
     * ????????????????????????
     */
    private ValueAnimator showValueAnimator;
    /**
     * ?????????????????????????????????
     */
    private int mPosition = 0;
    /**
     * ??????????????????
     */
    private int mAlivcControlViewWidth;
    /**
     * ??????????????????
     */
    private int mAlivcControlViewHeight;
    /**
     * displayName
     */
    private String userName;

    private RTCAuthInfo mRTCAuthInfo;
    /**
     * ???????????????????????????
     */
    private ChartUserBean mLocalChartUserBean;
    /**
     * ??????????????????????????????
     */
    private ChartUserBean mRemoteChartUserBean;
    /**
     * ??????????????????
     */
    private RelativeLayout mRlVideoCallBg;
    /**
     * ????????????
     */
    private FrameLayout mBigVideoViewContainer;
    /**
     * ????????????
     */
    private RecyclerView mVideoLiveRecylerView;

    /**
     * ?????????AliVideoCanvas
     */
    AliRtcEngine.AliVideoCanvas mLocalAliVideoCanvas;

    private LinearLayoutManager mLinearLayoutManager;


    /**
     * ?????????
     */
    private AlivcTimeTextView tvVideoCallDuration;
    private DisplayMetrics displayMetrics;

    private VideoCallHandler mHandler = new VideoCallHandler(this);


    /**
     * ???????????????????????????VideoCallView ??????????????????5??????????????????
     */
    private static class VideoCallHandler extends Handler {
        WeakReference<AlivcVideoCallView> mWeakReference;

        public VideoCallHandler(AlivcVideoCallView view) {
            mWeakReference = new WeakReference<AlivcVideoCallView>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            AlivcVideoCallView alivcVideoCallView = mWeakReference.get();
            if (alivcVideoCallView != null) {
                switch (msg.what) {
                    case HANDLER_HIDE_TASK:
                        if (IMAGE_BUTTON_IS_SHOW_FLAG && CAN_EXECUTE_HIDE_ANIMATOR) {
                            alivcVideoCallView.hideAlivcControlView();
                        } else {
                            sendEmptyMessageDelayed(HANDLER_HIDE_TASK, 5000);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public AlivcVideoCallView(Context context) {
        super(context);
        initView();
    }

    public AlivcVideoCallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public AlivcVideoCallView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    protected void initView() {
        LayoutInflater.from(getContext()).inflate(R.layout.aliyun_video_call_view, this, true);
        RTCBeaconTowerImpl.sharedInstance().setDelegate(this);
        initTimeTextView();
        initAliRtcView();
        initControlView();
        getDisplayMetrics();
        startPreview();

    }

    private void initTimeTextView() {
        tvVideoCallDuration = findViewById(R.id.tv_video_call_duration);
        tvVideoCallDuration.setVisibility(GONE);
    }

    /**
     * ?????????View,?????????????????????????????????
     */
    private void initAliRtcView() {
        //???????????????????????????
        ToastUtils.setGravity(Gravity.CENTER, 0, 0);
        mBigVideoViewContainer = findViewById(R.id.big_surface_container);
        mVideoLiveRecylerView = findViewById(R.id.alivc_video_call_remote_contentview);
        mUserVideoListAdapter = new ChartUserAdapter();
        initRecyclerView();
        mUserVideoListAdapter.setOnItemClickListener(new ChartUserAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ChartUserBean bean, View view, int position, long itemId) {
                exchangeViewToFullScreen(bean, position);
            }
        });

    }

    /**
     * ?????????????????????view
     */
    private void initControlView() {
        mRlVideoCallBg = findViewById(R.id.rl_video_call_bg);
        alivcControlView = findViewById(R.id.alivc_video_call_controlView);
        alivcControlView.post(new Runnable() {
            @Override
            public void run() {
                mAlivcControlViewWidth = alivcControlView.getMeasuredWidth();
                mAlivcControlViewHeight = alivcControlView.getMeasuredHeight();
                startTask();
            }
        });
        mRlVideoCallBg.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FastClickUtil.isFastClick()) {
                    return;
                }
                if (IMAGE_BUTTON_IS_SHOW_FLAG) {
                    hideAlivcControlView();
                } else {
                    showAlivcControlView();
                }
                IMAGE_BUTTON_IS_SHOW_FLAG = !IMAGE_BUTTON_IS_SHOW_FLAG;
            }
        });
        alivcControlView.setOnControlPanelListener(new AlivcControlView.OnControlPanelListener() {
            @Override
            public void onCameraPreview(boolean bool) {
                if (bool) {
                    RTCBeaconTowerImpl.sharedInstance().configLocalCameraPublish(false);
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_mutelocal_camera_off));
                } else {
                    RTCBeaconTowerImpl.sharedInstance().configLocalCameraPublish(true);
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_mutelocal_camera_on));

                }
            }

            /**
             * ??????
             */
            @Override
            public void onMute(boolean bool) {
                RTCBeaconTowerImpl.sharedInstance().muteLocalMic(bool);
                if (bool) {
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_mute_on));
                } else {
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_mute_off));

                }

            }

            /**
             * ??????
             */
            @Override
            public void onHandsFree(boolean bool) {
                RTCBeaconTowerImpl.sharedInstance().enableSpeakerphone(bool);
                if (bool) {
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_handsfree_on));
                } else {
                    ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_handsfree_off));

                }

            }

            /**
             * ???????????????
             */
            @Override
            public void onSwitchCamera() {
                Log.e(TAG, "onSwitchCamera");
                RTCBeaconTowerImpl.sharedInstance().switchCamera();
                ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_switch_camera));
            }

            /**
             * ??????
             */
            @Override
            public void onHangUp() {
                Log.e(TAG, "onHangUp leaveChannel");
                leave();
                if (alivcVideoCallNotifyListner != null) {
                    alivcVideoCallNotifyListner.onLeaveChannel();
                }
            }

            /**
             * ??????????????????
             */
            @Override
            public void onVoiceMode() {
                // switchVoiceModel();
            }

            @Override
            public void controlUp() {
                MqttServer.sentUp();
            }

            @Override
            public void controlDown() {
                MqttServer.sentDown();
            }

            @Override
            public void controlLeft() {
                MqttServer.sentLeft();
            }

            @Override
            public void controlRight() {
                MqttServer.sentRight();
            }
        });
    }

    /**
     * ??????????????????
     */
    private void getDisplayMetrics() {
        displayMetrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getMetrics(displayMetrics);
    }


    /**
     * ??????????????????
     */
    private void startTask() {
        if (mHandler != null) {
            mHandler.sendEmptyMessageDelayed(HANDLER_HIDE_TASK, 5000);
        }
    }

    /**
     * ????????????
     */
    private void showVideoCallDuration() {
        tvVideoCallDuration.start();
    }


    /**
     * ???????????????????????????
     *
     * @param chartUserBean
     */
    private void exchangeViewToFullScreen(ChartUserBean chartUserBean, int position) {

        if (chartUserBean == null) {
            return;
        }
        //?????????????????????ChartUserBean
        ChartUserBean bigChartUserBean = (ChartUserBean) mBigVideoViewContainer.getTag();
        //userId????????????????????????
        if (!bigChartUserBean.mUserId.equals(chartUserBean.mUserId)) {
            //????????????????????????????????????????????????
            updateRemoteBigViewDisplay(chartUserBean);
            // ????????????????????????????????????????????????????????????????????????????????????
            if (mLocalChartUserBean != null && !chartUserBean.mUserId.equals(mLocalChartUserBean.mUserId)) {
                //????????????????????????????????????
                mLocalChartUserBean.mCameraSurface = mLocalAliVideoCanvas.view;
                //???????????????????????????,??????????????????????????????????????????,???????????????
                if (mPosition == 0) {
                    mUserVideoListAdapter.updateData(mLocalChartUserBean, true);
                }
            }
            //?????????????????????uid????????????????????????????????????????????????item????????????
            if (mRemoteChartUserBean != null && !chartUserBean.mUserId.equals(mRemoteChartUserBean.mUserId)) {
                mUserVideoListAdapter.updateData(mRemoteChartUserBean, true);
            }
        }
        //?????????????????????
        mBigVideoViewContainer.setTag(chartUserBean);
        mRemoteChartUserBean = chartUserBean;
        mPosition = position;
    }

    /**
     * ????????????
     */
    private void startPreview() {
        SophonSurfaceView mBigVideoCallView = new SophonSurfaceView(AlivcVideoCallView.this.getContext());
        mBigVideoCallView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mLocalAliVideoCanvas = new AliRtcEngine.AliVideoCanvas();
        mLocalAliVideoCanvas.view = mBigVideoCallView;
        //??????????????????,???????????????
        mLocalAliVideoCanvas.renderMode = AliRtcRenderModeAuto;
        //??????LocalView
        mBigVideoViewContainer.removeAllViews();
        mBigVideoViewContainer.addView(mBigVideoCallView);
        RTCBeaconTowerImpl.sharedInstance().setLocalViewConfig(mLocalAliVideoCanvas, AliRtcVideoTrackCamera);
        RTCBeaconTowerImpl.sharedInstance().startPreview();
    }

    /**
     * ????????????????????????
     * ????????????????????????
     */
    private void updateRemoteBigViewDisplay(ChartUserBean chartUserBean) {
        //??????????????????????????????????????????
        if (chartUserBean.mScreenSurface != null) {
            ViewParent parent = chartUserBean.mScreenSurface.getParent();
            if (parent != null) {
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeAllViews();
                }
            }
            chartUserBean.mScreenSurface.setZOrderOnTop(false);
            mBigVideoViewContainer.removeAllViews();
            mBigVideoViewContainer.addView(chartUserBean.mScreenSurface);
            chartUserBean.mScreenSurface.setZOrderMediaOverlay(false);
            return;
        }
        //?????????
        if (chartUserBean.mCameraSurface != null) {
            ViewParent parent = chartUserBean.mCameraSurface.getParent();
            if (parent != null) {
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeAllViews();
                }
            }
            chartUserBean.mCameraSurface.setZOrderOnTop(false);
            mBigVideoViewContainer.removeAllViews();
            mBigVideoViewContainer.addView(chartUserBean.mCameraSurface);
            chartUserBean.mCameraSurface.setZOrderMediaOverlay(false);
        }
        if (chartUserBean.mScreenSurface == null && chartUserBean.mCameraSurface == null) {
            mBigVideoViewContainer.removeAllViews();
        }
    }


    private AliRtcAuthInfo getAliRtcAuthInfo(RTCAuthInfo mRtcAuthInfo, String channel) {
        AliRtcAuthInfo userInfo = new AliRtcAuthInfo();
        userInfo.setAppid(mRtcAuthInfo.data.appid);
        userInfo.setNonce(mRtcAuthInfo.data.nonce);
        userInfo.setTimestamp(mRtcAuthInfo.data.timestamp);
        userInfo.setUserId(mRtcAuthInfo.data.userid);
        userInfo.setGslb(mRtcAuthInfo.data.gslb);
        userInfo.setToken(mRtcAuthInfo.data.token);
        userInfo.setConferenceId(channel);
        return userInfo;
    }


    /**
     * ????????????????????????
     */
    public void auth(String displayName, String channel, RTCAuthInfo rtcAuthInfo) {
        userName = displayName;
        mRTCAuthInfo = rtcAuthInfo;
        AliRtcAuthInfo aliRtcAuthInfo = getAliRtcAuthInfo(rtcAuthInfo, channel);
        RTCBeaconTowerImpl.sharedInstance().joinChannel(aliRtcAuthInfo, displayName);
    }


    private void updateRemoteDisplay(String uid, AliRtcEngine.AliRtcAudioTrack at, AliRtcEngine.AliRtcVideoTrack vt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AliRtcRemoteUserInfo remoteUserInfo = RTCBeaconTowerImpl.sharedInstance().getUserInfo(uid);
                // ???????????????????????????????????????????????????????????????????????????????????????
                if (remoteUserInfo == null) {
                    // remote user exit room
                    Log.e(TAG, "updateRemoteDisplay remoteUserInfo = null, uid = " + uid);
                    return;
                }
                //change
                AliRtcEngine.AliVideoCanvas cameraCanvas = remoteUserInfo.getCameraCanvas();
                AliRtcEngine.AliVideoCanvas screenCanvas = remoteUserInfo.getScreenCanvas();
                //????????????
                if (vt == AliRtcVideoTrackNo) {
                    //???????????????
                    cameraCanvas = null;
                    screenCanvas = null;
                } else if (vt == AliRtcVideoTrackCamera) {
                    //?????????
                    screenCanvas = null;
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK???????????????????????????view
                    RTCBeaconTowerImpl.sharedInstance().setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                } else if (vt == AliRtcVideoTrackScreen) {
                    //?????????
                    cameraCanvas = null;
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK???????????????????????????view
                    RTCBeaconTowerImpl.sharedInstance().setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                } else if (vt == AliRtcVideoTrackBoth) {
                    //??????
                    cameraCanvas = createCanvasIfNull(cameraCanvas);
                    //SDK???????????????????????????view
                    RTCBeaconTowerImpl.sharedInstance().setRemoteViewConfig(cameraCanvas, uid, AliRtcVideoTrackCamera);
                    screenCanvas = createCanvasIfNull(screenCanvas);
                    //SDK???????????????????????????view
                    RTCBeaconTowerImpl.sharedInstance().setRemoteViewConfig(screenCanvas, uid, AliRtcVideoTrackScreen);
                } else {
                    return;
                }
                ChartUserBean chartUserBean = convertRemoteUserInfo(remoteUserInfo, cameraCanvas, screenCanvas);

                //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                if (mRemoteChartUserBean != null && mRemoteChartUserBean.mUserId.equals(chartUserBean.mUserId)) {
                    mRemoteChartUserBean = chartUserBean;
                    updateRemoteBigViewDisplay(chartUserBean);
                } else {
                    mUserVideoListAdapter.updateData(chartUserBean, true);
                }
            }
        });
    }

    private void addRemoteUser(String uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //????????????????????????
                AliRtcRemoteUserInfo remoteUserInfo = RTCBeaconTowerImpl.sharedInstance().getUserInfo(uid);
                if (remoteUserInfo != null) {
                    mUserVideoListAdapter.updateData(convertRemoteUserToUserData(remoteUserInfo), true);
                }
            }
        });
    }

    private void removeRemoteUser(String uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ChartUserBean chartUserBean = mUserVideoListAdapter.getDataByUid(uid);
                if (chartUserBean != null) {
                    String displayName = String.format(getContext().getResources().getString(R.string.alivc_video_call_channel), chartUserBean.mUserName);
                    ToastUtils.showShort(displayName);
                }
                mUserVideoListAdapter.removeData(uid, true);
            }
        });
    }

    private ChartUserBean convertRemoteUserToUserData(AliRtcRemoteUserInfo remoteUserInfo) {
        String uid = remoteUserInfo.getUserID();
        ChartUserBean ret = mUserVideoListAdapter.createDataIfNull(uid);
        ret.mUserId = uid;
        ret.mUserName = remoteUserInfo.getDisplayName();
        ret.mIsCameraFlip = false;
        ret.mIsScreenFlip = false;
        return ret;
    }

    private ChartUserBean convertRemoteUserInfo(AliRtcRemoteUserInfo remoteUserInfo,
                                                AliRtcEngine.AliVideoCanvas cameraCanvas,
                                                AliRtcEngine.AliVideoCanvas screenCanvas) {
        String uid = remoteUserInfo.getUserID();
        ChartUserBean ret = mUserVideoListAdapter.createDataIfNull(uid);
        ret.mUserId = remoteUserInfo.getUserID();
        ret.mUserName = remoteUserInfo.getDisplayName();
        ret.mCameraSurface = cameraCanvas != null ? cameraCanvas.view : null;
        ret.mIsCameraFlip = cameraCanvas != null && cameraCanvas.mirrorMode == AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled;

        ret.mScreenSurface = screenCanvas != null ? screenCanvas.view : null;
        ret.mIsScreenFlip = screenCanvas != null && screenCanvas.mirrorMode == AliRtcEngine.AliRtcRenderMirrorMode.AliRtcRenderMirrorModeAllEnabled;
        return ret;
    }

    private AliRtcEngine.AliVideoCanvas createCanvasIfNull(AliRtcEngine.AliVideoCanvas canvas) {
        if (canvas == null || canvas.view == null) {
            //??????canvas???Canvas???SophonSurfaceView??????????????????
            canvas = new AliRtcEngine.AliVideoCanvas();
            SophonSurfaceView surfaceView = new SophonSurfaceView(getContext());
            surfaceView.setZOrderOnTop(true);
            surfaceView.setZOrderMediaOverlay(true);
            canvas.view = surfaceView;
            //renderMode?????????????????????Auto???Stretch???Fill???Crop???????????????Auto?????????
            canvas.renderMode = AliRtcRenderModeAuto;
        }
        return canvas;
    }


    private AlivcVideoCallNotifyListner alivcVideoCallNotifyListner;

    public void setAlivcVideoCallNotifyListner(AlivcVideoCallNotifyListner
                                                       alivcVideoCallNotifyListner) {
        this.alivcVideoCallNotifyListner = alivcVideoCallNotifyListner;
    }

    public interface AlivcVideoCallNotifyListner {
        void onLeaveChannel();
    }

    /**
     * ??????AlivcControlView ?????????
     */
    private void showAlivcControlView() {
        if (alivcControlView != null && alivcControlView.getLayoutParams() != null &&
                !IMAGE_BUTTON_IS_SHOW_FLAG && CAN_EXECUTE_SHOW_ANIMATOR) {
            final MarginLayoutParams mMarginLayoutParams = (MarginLayoutParams) alivcControlView.getLayoutParams();
            showValueAnimator = ValueAnimator.ofFloat(-mAlivcControlViewHeight,
                    DensityUtil.dip2px(AlivcVideoCallView.this.getContext(),
                            getResources().getDimension(R.dimen.margin_3)));
            showValueAnimator.setDuration(500);
            showValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentMargin = (float) animation.getAnimatedValue();
                    mMarginLayoutParams.bottomMargin = (int) currentMargin;
                    alivcControlView.setLayoutParams(mMarginLayoutParams);
                }
            });
            showValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    CAN_EXECUTE_HIDE_ANIMATOR = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    CAN_EXECUTE_HIDE_ANIMATOR = true;
                    IMAGE_BUTTON_IS_SHOW_FLAG = true;
                    if (mHandler != null) {
                        mHandler.removeCallbacksAndMessages(null);
                        mHandler.sendEmptyMessageDelayed(HANDLER_HIDE_TASK, 5000);
                    }
                }
            });
            showValueAnimator.start();
        }
    }

    /**
     * ??????AlivcControlView ?????????
     */
    private void hideAlivcControlView() {
        if (alivcControlView != null && alivcControlView.getLayoutParams() != null &&
                IMAGE_BUTTON_IS_SHOW_FLAG && CAN_EXECUTE_HIDE_ANIMATOR) {
            final MarginLayoutParams mMarginLayoutParams = (MarginLayoutParams) alivcControlView.getLayoutParams();
            hideValueAnimator = ValueAnimator.ofFloat(DensityUtil.dip2px(AlivcVideoCallView.this.getContext(),
                    getResources().getDimension(R.dimen.margin_3)), -mAlivcControlViewHeight);
            hideValueAnimator.setDuration(500);
            hideValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentMargin = (float) animation.getAnimatedValue();
                    mMarginLayoutParams.bottomMargin = (int) currentMargin;
                    alivcControlView.setLayoutParams(mMarginLayoutParams);
                }
            });
            hideValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    CAN_EXECUTE_SHOW_ANIMATOR = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    CAN_EXECUTE_SHOW_ANIMATOR = true;
                    IMAGE_BUTTON_IS_SHOW_FLAG = false;
                }
            });
            hideValueAnimator.start();
        }
    }

    /**
     * ?????????RecyclerView
     */
    private void initRecyclerView() {
        mLinearLayoutManager = new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false);
        mVideoLiveRecylerView.setLayoutManager(mLinearLayoutManager);
        mVideoLiveRecylerView.addItemDecoration(new BaseRecyclerViewAdapter.DividerGridItemDecoration(
                getResources().getDrawable(R.drawable.chart_content_userlist_item_divider)));
        DefaultItemAnimator anim = new DefaultItemAnimator();
        anim.setSupportsChangeAnimations(false);
        mVideoLiveRecylerView.setItemAnimator(anim);
        mVideoLiveRecylerView.setAdapter(mUserVideoListAdapter);
    }

    public void leave() {
        if (tvVideoCallDuration != null) {
            tvVideoCallDuration.stop();
            tvVideoCallDuration = null;
        }
        if (showValueAnimator != null) {
            showValueAnimator.cancel();
            showValueAnimator.removeAllUpdateListeners();
        }
        if (hideValueAnimator != null) {
            hideValueAnimator.cancel();
            hideValueAnimator.removeAllUpdateListeners();
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        RTCBeaconTowerImpl.sharedInstance().logout();
        RTCBeaconTowerImpl.sharedInstance().destorySharedInstance();
    }

    @Override
    public void onRemoteUserOnLineNotify(String userId) {
        Log.i(TAG, "onRemoteUserOnLineNotify: result" + userId);
        addRemoteUser(userId);
    }

    @Override
    public void onRemoteUserOffLineNotify(String userId) {
        Log.i(TAG, "onRemoteUserOffLineNotify: " + userId);
        //????????????????????????
        String[] onlineRemoteUsers = RTCBeaconTowerImpl.sharedInstance().getOnlineRemoteUsers();
        Log.i(TAG, "onRemoteUserOffLineNotify number    : " + onlineRemoteUsers.length);
        if (onlineRemoteUsers.length == 0) {
            if (tvVideoCallDuration != null) {
                tvVideoCallDuration.setVisibility(GONE);
                tvVideoCallDuration.stop();
            }
        }
        removeRemoteUser(userId);
    }

    @Override
    public void onJoinChannelResult(int result) {
        Log.d(TAG, "onJoinChatResult " + result);
        ToastUtils.showShort(getResources().getString(R.string.aliyun_tips_waiting));
        mLocalChartUserBean = new ChartUserBean();
        mLocalChartUserBean.mUserId = mRTCAuthInfo.data.userid;
        mLocalChartUserBean.mIsLocal = true;
        mLocalChartUserBean.mUserName = "??????";
        mBigVideoViewContainer.setTag(mLocalChartUserBean);
        //??????tag
        mBigVideoViewContainer.setTag(mLocalChartUserBean);
        //??????????????????????????????????????????????????????????????????
        mUserVideoListAdapter.addDataByPosition(mLocalChartUserBean, true);
    }

    @Override
    public void onRemoteTrackAvailableNotify(String userId, AliRtcEngine.AliRtcAudioTrack audioTrack, AliRtcEngine.AliRtcVideoTrack videoTrack) {
        Log.d(TAG, "onRemoteTrackAvailableNotify: result" + userId + "____" + audioTrack + "????????????" + videoTrack);
        //updateRemoteDisplay(userId, audioTrack, videoTrack);
    }

    @Override
    public void onSubscribeChangedNotify(String userId, AliRtcEngine.AliRtcAudioTrack audioTrack, AliRtcEngine.AliRtcVideoTrack videoTrack) {
        if (!(audioTrack == AliRtcAudioTrackNo && videoTrack == AliRtcVideoTrackNo)) {
            //???????????????????????????????????????
            if (tvVideoCallDuration != null && tvVideoCallDuration.getVisibility() == GONE) {
                showVideoCallDuration();
            }
            updateRemoteDisplay(userId, audioTrack, videoTrack);
        }
    }

    @Override
    public void onNetworkQualityChanged(String userId, AliRtcEngine.AliRtcNetworkQuality aliRtcNetworkQuality, AliRtcEngine.AliRtcNetworkQuality aliRtcNetworkQuality1) {

    }
}
