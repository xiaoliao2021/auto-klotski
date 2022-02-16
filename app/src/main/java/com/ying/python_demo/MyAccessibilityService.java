package com.ying.python_demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

public class MyAccessibilityService extends AccessibilityService {
    public static MyAccessibilityService service;
    public static MyCallBack callBack;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        service = this;
        Log.e("MyAccessibilityService", "onServiceConnected");
        if (callBack != null) {
            callBack.onStart();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
//        Log.e("MyAccessibilityService","onAccessibilityEvent");
    }

    @Override
    public void onInterrupt() {

    }

    public void MyGesture() {//仿滑动
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(100, 100);//滑动起点
            path.lineTo(500, 500);//滑动终点
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription description = builder.addStroke(new GestureDescription.StrokeDescription(path, 100L, 1000L)).build();
            //100L 第一个是开始的时间，第二个是持续时间
            dispatchGesture(description, null, null);
            Log.e("MyAccessibilityService", "MyGesture111");
        } else {
            Log.e("MyAccessibilityService", "MyGesture222");
        }
    }

    public void swipe(String[] ss) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            float sx = Float.parseFloat(ss[0]);
            float sy = Float.parseFloat(ss[1]);
            float ex = Float.parseFloat(ss[2]);
            float ey = Float.parseFloat(ss[3]);
            long duration = Long.parseLong(ss[4]);
            path.moveTo(sx, sy);//滑动起点
            path.lineTo(ex, ey);//滑动终点
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription description = builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, duration)).build();
            dispatchGesture(description, null, null);
        }
    }
    public void swipe2(Path path,long duration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription description = builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, duration)).build();
            dispatchGesture(description, null, null);
        }
    }

}
