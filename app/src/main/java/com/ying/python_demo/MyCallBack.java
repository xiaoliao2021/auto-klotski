package com.ying.python_demo;

import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public abstract class MyCallBack {
    public MyCallBack() {

    }
    abstract public void onStart();
}
