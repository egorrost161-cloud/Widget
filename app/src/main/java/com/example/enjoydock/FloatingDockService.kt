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
        val isLocked = prefs.getBoolean("is_locked", false)
        
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

        dockView = CustomDockLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingLeftRight, paddingTopBottom, paddingLeftRight, paddingTopBottom)
            
            val bg = GradientDrawable().apply {
                setColor(bgColor) 
                this.cornerRadius = cornerRadius
            }
            background = bg
            
            this.isLocked = isLocked
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
        
        // ВОССТАНАВЛИВАЕМ ПОЗИЦИЮ ИЗ ПАМЯТИ (или ставим по умолчанию)
        params.x = prefs.getInt("dock_x", 50)
        params.y = prefs.getInt("dock_y", 400)

        // --- ЛОГИКА ПЕРЕТАСКИВАНИЯ ---
        var startX = 0
        var startY = 0

        dockView?.onDragStart = {
            // Запоминаем, где виджет был в момент начала перетаскивания
            startX = params.x
            startY = params.y
        }

        dockView?.onDrag = { dx, dy ->
            // Вычисляем новую позицию: стартовая + смещение пальца
            params.x = startX + dx
            params.y = startY + dy
            windowManager.updateViewLayout(dockWrapper, params)
        }

        dockView?.onDragEnd = {
            // СОХРАНЯЕМ НОВУЮ ПОЗИЦИЮ В ПАМЯТЬ, чтобы при блокировке или перезагрузке он не улетал обратно
            prefs.edit()
                .putInt("dock_x", params.x)
                .putInt("dock_y", params.y)
                .apply()
        }

        windowManager.addView(dockWrapper, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dockWrapper != null) windowManager.removeView(dockWrapper)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
