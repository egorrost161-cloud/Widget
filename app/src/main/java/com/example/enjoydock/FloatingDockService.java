package com.example.enjoydock;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class FloatingDockService extends Service {

    private WindowManager windowManager;
    private DockScrollView dockWrapper;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("dock_settings", Context.MODE_PRIVATE);
        createDock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String channelId = "dock_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Плавающий виджет", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Виджет")
                    .setContentText("Виджет запущен")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }

        if (dockWrapper != null) {
            windowManager.removeView(dockWrapper);
            createDock();
        }
        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createDock() {
        boolean isLocked = prefs.getBoolean("is_locked", false);
        int transparency = prefs.getInt("dock_transparency", 80);
        int alpha = (transparency * 255) / 100;
        String alphaHex = String.format("%02X", alpha);
        int bgColor = Color.parseColor("#" + alphaHex + "222222");

        int scalePercent = prefs.getInt("dock_size", 100);
        float scale = scalePercent / 100f;

        int paddingLR = (int) (20 * scale);
        int paddingTB = (int) (10 * scale);
        float cornerRadius = 30f * scale;
        int iconSize = (int) (120 * scale);
        int iconMargin = (int) (15 * scale);

        LinearLayout dockLayout = new LinearLayout(this);
        dockLayout.setOrientation(LinearLayout.HORIZONTAL);
        dockLayout.setGravity(Gravity.CENTER_VERTICAL);
        dockLayout.setPadding(paddingLR, paddingTB, paddingLR, paddingTB);

        for (String pkg : prefs.getStringSet("selected_apps", new java.util.HashSet<>())) {
            try {
                ImageView imageView = new ImageView(this);
                imageView.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
                lp.setMargins(iconMargin, 0, iconMargin, 0);
                imageView.setLayoutParams(lp);
                final String finalPkg = pkg;
                imageView.setOnClickListener(v -> {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(finalPkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { startActivity(launchIntent); } catch (Exception e) {}
                    }
                });
                dockLayout.addView(imageView);
            } catch (Exception e) {}
        }

        dockWrapper = new DockScrollView(this);
        dockWrapper.setHorizontalScrollBarEnabled(false);
        dockWrapper.addView(dockLayout);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(cornerRadius);
        dockWrapper.setBackground(bg);
        dockWrapper.setClipToOutline(true);

        // === ПЕРЕДАЕМ РЕЖИМ БЛОКИРОВКИ В КЛАСС ===
        dockWrapper.setLocked(isLocked);

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);

        params = new WindowManager.LayoutParams(
                maxWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("dock_x", 50);
        params.y = prefs.getInt("dock_y", 400);

        // Привязываем перетаскивание ТОЛЬКО если НЕ заблокировано
        if (!isLocked) {
            dockWrapper.setStartPosition(params.x, params.y);

            dockWrapper.setDragListener(new DockScrollView.OnDragListener() {
                @Override
                public void onDrag(int x, int y) {
                    params.x = x;
                    params.y = y;
                    windowManager.updateViewLayout(dockWrapper, params);
                }

                @Override
                public void onDragEnd() {
                    prefs.edit()
                            .putInt("dock_x", params.x)
                            .putInt("dock_y", params.y)
                            .apply();
                }
            });
        }

        windowManager.addView(dockWrapper, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dockWrapper != null) windowManager.removeView(dockWrapper);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
