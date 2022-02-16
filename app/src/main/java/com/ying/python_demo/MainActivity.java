package com.ying.python_demo;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    public Handler handler = new Handler();
    public Python py = null;
    public MyAccessibilityService service;
    public ByteBuffer buffer;
    public int pixelStride, rowStride, screenWidth, screenHeight;
    public boolean workGetScreen = false;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start();
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }
        py = Python.getInstance();
        MyAccessibilityService.callBack = new MyCallBack() {
            @Override
            public void onStart() {
                service = MyAccessibilityService.service;
                Toast.makeText(getApplicationContext(), "onStart", Toast.LENGTH_LONG).show();
            }
        };
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        startFloatingButtonService();
        FloatingButtonService.onClickListener = view -> {
            Log.e("TAG", "onClickListener");
            getImage();
//            Path path = new Path();
//            path.moveTo(488, 371);
//            for (int i = 484; i > 360; i -= (Math.random() * 8)) {
//                path.lineTo(i, 371);
//            }
//            path.lineTo(360-100, 371);
//            MyAccessibilityService.service.swipe2(path, 400L);
        };
    }

    public void onClick(View view) {
//        startService(new Intent(this, MyAccessibilityService.class));
//        if (service != null) {
//            service.MyGesture();
//        }
    }

    public void start() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, 333);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 333 && data != null) {
            workGetScreen = true;
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data);
            DisplayMetrics outMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
            screenWidth = outMetrics.widthPixels;
            screenHeight = outMetrics.heightPixels;
            ImageReader imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888, 10);
            VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay("screen-mirror",
                    screenWidth,
                    screenHeight,
                    outMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            new Thread(() -> {
                while (workGetScreen) {
                    Image image = imageReader.acquireLatestImage();
                    if (image == null) {
                        continue;
                    }
                    Image.Plane[] planes = image.getPlanes();
                    buffer = planes[0].getBuffer();
                    pixelStride = planes[0].getPixelStride();
//                    Log.e("TAG", "pixelStride" + pixelStride);
                    rowStride = planes[0].getRowStride();
                    image.close();
                }
                mediaProjection.stop();
                virtualDisplay.release();
            }).start();

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startFloatingButtonService() {
        if (FloatingButtonService.isStarted) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "当前无权限，请授权", Toast.LENGTH_SHORT);
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
        } else {
            startService(new Intent(MainActivity.this, FloatingButtonService.class));
        }
    }

    public void getImage() {
        int rowPadding = rowStride - pixelStride * screenWidth;
        Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        String fileName = null;
        try {
            File dir = getExternalFilesDir(null);
            fileName = dir.getAbsolutePath() + "/" + "test.png";
            Log.e("fileName", fileName);
            FileOutputStream fos = new FileOutputStream(fileName);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            String finalFileName = fileName;
            new Thread(() -> {
//                String res = py.getModule("main").callAttr("get_input_list", finalFileName).toJava(String.class);
//                Log.e("TAG", res);
                String res = py.getModule("main").callAttr("main", finalFileName).toJava(String.class);
                handler.postDelayed(()->{
                    Toast.makeText(getApplicationContext(),"开始滑动",Toast.LENGTH_LONG).show();
                },0);
                for (String s : res.split(",")) {
                    MyAccessibilityService.service.swipe(s.split(" "));
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                handler.postDelayed(()->{
                    Toast.makeText(getApplicationContext(),"结束滑动",Toast.LENGTH_LONG).show();
                },0);
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}