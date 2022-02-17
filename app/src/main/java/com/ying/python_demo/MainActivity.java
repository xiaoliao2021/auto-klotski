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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

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
                class Block {
                    public int row;
                    public int col;
                    public int len;
                    public boolean is_horizontal;

                    public Block(int val) {
                        is_horizontal = (val & (1 << 7)) != 0;
                        len = ((val & (1 << 6)) >> 6) + 2;
                        row = (val >> 3) & ((1 << 3) - 1);
                        col = val & ((1 << 3) - 1);
                    }
                }
                String res = py.getModule("main").callAttr("get_input_list", finalFileName).toJava(String.class);
                Log.e("TAG", res);
                String[] input = res.substring(1, res.length() - 1).split(", ");
                Block[] blocks = new Block[input.length];
                for (int i = 0; i < input.length; i++) {
                    blocks[i] = new Block(Integer.parseInt(input[i]));
                }
                ArrayList<Integer> ret = solve(input);
                handler.postDelayed(() -> {
                    Toast.makeText(getApplicationContext(), "开始滑动:" + (ret.size() - 1), Toast.LENGTH_LONG).show();
                }, 0);
                int[] position = new int[]{45, 300, 673 - 45, 930 - 300};
                int step_len = (position[2] + position[3]) / 12;
                for (Integer key : ret) {
                    int idx = (key >> 3);
                    int step = (key & (((1 << 3) - 1)));
                    Log.e("TAG", "idx:" + idx + "step:" + step);
                    step = ((step & 0b100) != 0) ? -(step & 0b11) - 1 : step + 1;
                    Log.e("TAG", "idx:" + idx + "step:" + step);
                    Block op = blocks[idx];
                    int s_x, e_x, s_y, e_y;
                    s_x = e_x = (int) (position[0] + (op.col + 0.5) * step_len);
                    s_y = e_y = (int) (position[1] + (op.row + 0.5) * step_len);
                    if (op.is_horizontal) {
                        op.col += step;
                        e_x = (int) (position[0] + (op.col + 0.5) * step_len);
                    } else {
                        op.row += step;
                        e_y = (int) (position[1] + (op.row + 0.5) * step_len);
                    }
                    Path path = new Path();
                    path.moveTo(s_x, s_y);
                    path.lineTo(e_x, e_y);
                    if (MyAccessibilityService.service != null) {
                        MyAccessibilityService.service.swipe2(path, 400);
                    }
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
//                String res2 = py.getModule("main").callAttr("main", finalFileName).toJava(String.class);
//                for (String s : res2.split(",")) {
//                    Log.e("TAG", s);
//                }
                handler.postDelayed(() -> {
                    Toast.makeText(getApplicationContext(), "结束滑动", Toast.LENGTH_LONG).show();
                }, 0);
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean check_target(int val) {
        boolean is_horizontal = (val & (1 << 7)) != 0;
        int row = (val >> 3) & ((1 << 3) - 1);
        int col = val & ((1 << 3) - 1);
        return is_horizontal && row == 2 && col == 4;
    }

    public long get_map_val(int[] block_values) {
        long map_val = 0;
        for (int val : block_values) {
            boolean is_horizontal = (val & (1 << 7)) != 0;
            int len = ((val & (1 << 6)) >> 6) + 2;
            int row = (val >> 3) & ((1 << 3) - 1);
            int col = val & ((1 << 3) - 1);
            long mask = 0;
            for (int i = 0; i < len; ++i) {
                mask <<= (is_horizontal ? 1 : 6);
                mask += 1;
            }
            map_val |= (mask << (row * 6 + col));
        }
        return map_val;
    }

    public int check(int val, int step, long map_val) {
        boolean is_horizontal = (val & (1 << 7)) != 0;
        int len = ((val & (1 << 6)) >> 6) + 2;
        int row = (val >> 3) & ((1 << 3) - 1);
        int col = val & ((1 << 3) - 1);
        int new_s_col = col;
        int new_e_col = col + (is_horizontal ? step : 0);
        int new_s_row = row;
        int new_e_row = row + (is_horizontal ? 0 : step);
        if (is_horizontal) {
            new_s_col = col + step;
            new_e_col = new_s_col + len;
        } else {
            new_s_row = row + step;
            new_e_row = new_s_row + len;
        }
        if (new_s_col < 0 || new_e_col > 6 || new_s_row < 0 || new_e_row > 6) return 0;
        long mask = 0;
        for (int i = 0; i < len; ++i) {
            mask <<= (is_horizontal ? 1 : 6);
            mask += 1;
        }
        map_val &= (~(mask << (row * 6 + col)));
        if ((map_val & (mask << (new_s_row * 6 + new_s_col))) != 0) return 0;
        int new_val = 1 << 8;
        new_val |= (is_horizontal ? 1 << 7 : 0);
        new_val |= ((len - 2) << 6);
        new_val |= (new_s_row << 3);
        new_val |= new_s_col;
        return new_val;
    }

    public ArrayList<Integer> solve(String[] input) {
        HashSet<Long> hashSet = new HashSet<>();
        int len = input.length;
        int[] data = new int[len];
        len = 0;
        for (String s : input) {
            data[len++] = Integer.parseInt(s);
        }
        Queue<int[]> queue = new LinkedList<>();
        Queue<ArrayList<Integer>> queue_res = new LinkedList<>();
        queue.add(data);
        queue_res.add(new ArrayList<>());
        while (!queue.isEmpty()) {
            int[] now = queue.poll();
            ArrayList<Integer> now_res = queue_res.poll();
            if (check_target(now[0])) {
                return now_res;
            }
            long map_val = get_map_val(now);
            if (hashSet.contains(map_val)) continue;
            hashSet.add(map_val);
            for (int i = 0; i < len; i++) {
                for (int j = 1; j < 5; ++j) {
                    int val = check(now[i], j, map_val);
                    if (val != 0) {
                        int[] tmp = Arrays.copyOf(now, len);
                        tmp[i] = val;
                        ArrayList<Integer> new_res = new ArrayList<>(now_res);
                        new_res.add((j - 1) | (i << 3));
                        queue.add(tmp);
                        queue_res.add(new_res);
                    } else {
                        break;
                    }
                }
                for (int j = 1; j < 5; ++j) {
                    int val = check(now[i], -j, map_val);
                    if (val != 0) {
                        int[] tmp = Arrays.copyOf(now, len);
                        tmp[i] = val;
                        ArrayList<Integer> new_res = new ArrayList<>(now_res);
                        new_res.add((j + 3) | (i << 3));
                        queue.add(tmp);
                        queue_res.add(new_res);
                    } else {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>();
    }
}