package com.aliyun.apsaravideo.sophon.control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.alirtc.beacontowner.R;
import com.aliyun.apsaravideo.sophon.base.BaseActivity;
import com.aliyun.apsaravideo.sophon.mqtt.MqttServer;

/**
 * 小车控制界面
 */
public class ControlActivity extends BaseActivity {
    private Button btnControlUp;
    private Button btnControlDown;
    private Button btnControlLeft;
    private Button btnControlRight;
    @Override
    protected void initViews(Bundle savedInstanceState) {
        try {
            btnControlUp = findViewById(R.id.button_up);
            btnControlDown = findViewById(R.id.button_down);
            btnControlLeft = findViewById(R.id.button_left);
            btnControlRight = findViewById(R.id.button_right);
            Intent intent = new Intent(this, MqttServer.class);
            startService(intent);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_control;
    }

    public void controlUp(View view){
        MqttServer.sentUp();
    }
    public void controlDown(View view){
        MqttServer.sentDown();
    }
    public void controlLeft(View view){
        MqttServer.sentLeft();
    }
    public void controlRight(View view){
        MqttServer.sentRight();
    }
}
