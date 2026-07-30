package com.google.android.youtube.pro.webview;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import android.widget.TextView;

// Import the main files from the parent package
import com.google.android.youtube.pro.MainActivity;
import com.google.android.youtube.pro.R;

public class YTProWebChromeClient extends WebChromeClient {
    private final MainActivity activity;
    private final YTProWebView web;
    
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private int mOriginalOrientation;
    private int mOriginalSystemUiVisibility;
    private View mExitFullscreenButton;

    public YTProWebChromeClient(MainActivity activity, YTProWebView web) {
        this.activity = activity;
        this.web = web;
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
       return BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), 2130837573);
    }

    @Override
    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
        // 1. Determine orientation for FULL SCREEN
        mOriginalOrientation = activity.portrait ?
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

        if (activity.isPip) mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(params);
        }

        if (mCustomView != null) {
            onHideCustomView();
            return;
        }

        mCustomView = paramView;
        mOriginalSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
        
        // 2. Set the activity to full screen orientation (Landscape usually)
        activity.setRequestedOrientation(mOriginalOrientation);
        
        // Store portrait so onHideCustomView knows what to go back to
        mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        mCustomViewCallback = viewCallback;
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        decor.addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
        activity.getWindow().getDecorView().setSystemUiVisibility(3846);

        // Native, always-reachable exit-fullscreen control. The page's own
        // in-page collapse button isn't reliably reachable once WebView has
        // swapped in this custom native view, so we don't depend on it.
        addExitFullscreenButton(decor);
    }

    private void addExitFullscreenButton(FrameLayout decor) {
        TextView btn = new TextView(activity);
        btn.setText("\u2715"); // ×
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(Color.parseColor("#99000000"));

        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, activity.getResources().getDisplayMetrics());
        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, activity.getResources().getDisplayMetrics());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = marginPx;
        lp.rightMargin = marginPx;

        btn.setOnClickListener(v -> onHideCustomView());

        decor.addView(btn, lp);
        mExitFullscreenButton = btn;
    }

    @Override
    public void onHideCustomView() {
        if (mCustomView == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            activity.getWindow().setAttributes(params);
        }

        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        if (mExitFullscreenButton != null) {
            decor.removeView(mExitFullscreenButton);
            mExitFullscreenButton = null;
        }
        decor.removeView(mCustomView);
        mCustomView = null;
        activity.getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
        
        // 3. Set the activity BACK to the orientation saved right after going full screen (Portrait)
        activity.setRequestedOrientation(mOriginalOrientation);
        
        // Reset state for the next time we enter full screen
        mOriginalOrientation = activity.portrait ?
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

        if (mCustomViewCallback != null) {
            mCustomViewCallback.onCustomViewHidden();
        }
        mCustomViewCallback = null;
        web.clearFocus();
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            } else {
                request.grant(request.getResources());
            }
        }
    }
}