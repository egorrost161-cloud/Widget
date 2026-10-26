package com.example.enjoydock

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout

class FloatingDockService : Service() {

    private lateinit var windowManager: WindowManager
    private var dockWrapper: HorizontalScrollView? = null
    private var dockView: CustomDockLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createDock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "dock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Плавающий виджет", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Виджет")
            .setContentText("Виджет запущен и работает")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        if (dockWrapper != null) {
            windowManager.removeView(dockWrapper)
            createDock()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDock() {
        val prefs = getSharedPreferences("dock_settings", Context.MODE_PRIVATE)
        val selectedPackages = prefs.getStringSet("selected_apps", HashSet()) ?: HashSet()
        val isLocked = prefs.getBoolean("is_locked", false) // Читаем настройку блокировки
        
        val transparency = prefs.getInt("dock_transparency", 80)
        val alpha = (transparency * 255) / 100
        val alphaHex = Integer.toHexString(alpha).padStart(2, '0')
        val bgColor = Color.parseColor("#${alphaHex}222222")

        val scalePercent = prefs.getInt("dock_size", 100)
        val scale = scalePercent / 100f

        val paddingLeftRight = (20 * scale).toInt()
        val paddingTopBottom = (10 * scale).toInt()
        val cornerRadius = 30f * scale
        val iconSize = (120 * scale).toInt()
        val iconMargin = (15 * scale).toInt()

        // Используем наш кастомный класс
        dockView = CustomDockLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingLeftRight, paddingTopBottom, paddingLeftRight, paddingTopBottom)
            
            val bg = GradientDrawable().apply {
                setColor(bgColor) 
                this.cornerRadius = cornerRadius
            }
            background = bg
            
            // Передаем настройку блокировки
            this.isLocked = isLocked
            
            // Обработка перетаскивания
            this.onDrag = { dx, dy ->
                params.x += dx
                params.y += dy
                // Корректируем начальную точку касания, чтобы не было рывков
                // (в CustomDockLayout это уже учтено через rawX/rawY)
                windowManager.updateViewLayout(dockWrapper, params)
            }
        }

        val pm = packageManager
        for (pkg in selectedPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(appInfo)

                val imageView = ImageView(this).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        setMargins(iconMargin, 0, iconMargin, 0)
                    }
                    setOnClickListener {
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { startActivity(launchIntent) } catch (e: Exception) {}
                    }
                }
                dockView?.addView(imageView)
            } catch (e: Exception) {}
        }

        dockWrapper = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(dockView)
        }

        val displayMetrics = resources.displayMetrics
        val maxWidth = (displayMetrics.widthPixels * 0.9).toInt()

        params = WindowManager.LayoutParams(
            maxWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 400

        windowManager.addView(dockWrapper, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dockWrapper != null) windowManager.removeView(dockWrapper)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
