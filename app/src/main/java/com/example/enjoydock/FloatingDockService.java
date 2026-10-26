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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class FloatingDockService extends Service {

    private WindowManager windowManager;
    private LinearLayout dockLayout;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;

    // Переменные для перетаскивания
    private float touchStartX, touchStartY;
    private int startX, startY;
    private boolean isDragging = false;

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

        if (dockLayout != null) {
            windowManager.removeView(dockLayout);
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

        // 1. Создаем обычный контейнер (без ScrollView)
        dockLayout = new LinearLayout(this);
        dockLayout.setOrientation(LinearLayout.HORIZONTAL);
        dockLayout.setGravity(Gravity.CENTER_VERTICAL);
        dockLayout.setPadding(paddingLR, paddingTB, paddingLR, paddingTB);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(cornerRadius);
        dockLayout.setBackground(bg);

        // 2. Загружаем иконки
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

        // 3. Параметры окна (ширина по содержимому)
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("dock_x", 50);
        params.y = prefs.getInt("dock_y", 400);

        // 4. Логика перетаскивания
        dockLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (isLocked) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        isDragging = false;
                        return false; // Пропускаем к иконкам
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        
                        if (isDragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            windowManager.updateViewLayout(dockLayout, params);
                            return true; // Забираем событие, чтобы не нажимались иконки
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            prefs.edit()
                                    .putInt("dock_x", params.x)
                                    .putInt("dock_y", params.y)
                                    .apply();
                        }
                        return false;
                }
                return false;
            }
        });

        windowManager.addView(dockLayout, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dockLayout != null) windowManager.removeView(dockLayout);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
