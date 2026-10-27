package com.example.enjoydock;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FloatingDockService extends Service {

    private WindowManager windowManager;
    private DockScrollView dockWrapper;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private UsageStatsManager usageStatsManager;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isCurrentlyHidden = false;

    private int startWindowX, startWindowY;

    private final Runnable visibilityTask = new Runnable() {
        @Override
        public void run() {
            checkActiveApp();
            handler.postDelayed(this, 250); 
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        prefs = getSharedPreferences("dock_settings", Context.MODE_PRIVATE);
        createDock();
        handler.post(visibilityTask);
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
        final float cornerRadius = 30f * scale;
        int iconSize = (int) (120 * scale);
        int iconMargin = (int) (15 * scale);

        LinearLayout dockLayout = new LinearLayout(this);
        dockLayout.setOrientation(LinearLayout.HORIZONTAL);
        dockLayout.setGravity(Gravity.CENTER_VERTICAL);
        dockLayout.setPadding(paddingLR, paddingTB, paddingLR, paddingTB);

        String savedOrderString = prefs.getString("selected_apps_order", "");
        List<String> selectedApps;
        
        if (savedOrderString.isEmpty()) {
            Set<String> oldSet = prefs.getStringSet("selected_apps", new HashSet<>());
            selectedApps = new ArrayList<>(oldSet);
        } else {
            selectedApps = Arrays.asList(savedOrderString.split(","));
        }

        int iconCount = selectedApps.size();

        for (String pkg : selectedApps) {
            if (pkg.isEmpty()) continue;
            try {
                ImageView imageView = new ImageView(this);
                imageView.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
                lp.setMargins(iconMargin, 0, iconMargin, 0);
                imageView.setLayoutParams(lp);
                final String finalPkg = pkg;
                imageView.setOnClickListener(v -> launchApp(finalPkg));
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

        dockWrapper.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        dockWrapper.setClipToOutline(true);
        
        dockWrapper.setLocked(isLocked);

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        int calculatedWidth = (iconCount * (iconSize + (iconMargin * 2))) + (paddingLR * 2);
        int finalWidth = Math.min(calculatedWidth, maxWidth);

        params = new WindowManager.LayoutParams(
                finalWidth, 
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

        if (!isLocked) {
            dockWrapper.setDragListener(new DockScrollView.OnDragListener() {
                @Override
                public void onDragStart() {
                    startWindowX = params.x;
                    startWindowY = params.y;
                }

                @Override
                public void onDrag(float dx, float dy) {
                    params.x = startWindowX + (int) dx;
                    params.y = startWindowY + (int) dy;
                    windowManager.updateViewLayout(dockWrapper, params);
                }

                @Override
                public void onDragEnd() {
                    prefs.edit().putInt("dock_x", params.x).putInt("dock_y", params.y).apply();
                }
            });
        }

        windowManager.addView(dockWrapper, params);
    }

    // НОВЫЙ МЕТОД: Умный запуск приложений (включая системные)
    private void launchApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(pkg);

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launchIntent);
                return;
            } catch (Exception ignored) {}
        }

        // Если обычный запуск не удался (системное приложение), пробуем найти его главную Activity
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(pkg);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        if (!resolveInfos.isEmpty()) {
            ResolveInfo info = resolveInfos.get(0);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(pkg, info.activityInfo.name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }

        // Если и это не сработало - пробуем открыть настройки приложения
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void checkActiveApp() {
        if (usageStatsManager == null || dockWrapper == null) return;
        try {
            long time = System.currentTimeMillis();
            UsageEvents events = usageStatsManager.queryEvents(time - 2000, time); 
            String lastPkg = null;
            UsageEvents.Event event = new UsageEvents.Event();
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.getPackageName();
                }
            }
            
            if (lastPkg == null) return;
            
            Set<String> hiddenApps = prefs.getStringSet("hidden_apps", new HashSet<>());
            boolean shouldHide = hiddenApps.contains(lastPkg);

            if (shouldHide && !isCurrentlyHidden) {
                dockWrapper.setVisibility(View.GONE);
                isCurrentlyHidden = true;
            } else if (!shouldHide && isCurrentlyHidden) {
                dockWrapper.setVisibility(View.VISIBLE);
                isCurrentlyHidden = false;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(visibilityTask);
        if (dockWrapper != null) windowManager.removeView(dockWrapper);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
