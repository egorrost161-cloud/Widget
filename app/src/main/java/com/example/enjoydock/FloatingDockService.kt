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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FloatingDockService : Service() {

    private lateinit var windowManager: WindowManager
    private var dockWrapper: HorizontalScrollView? = null
    private var dockView: LinearLayout? = null
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
        val textSizeBtn = 24f * scale

        dockView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingLeftRight, paddingTopBottom, paddingLeftRight, paddingTopBottom)
            
            val bg = GradientDrawable().apply {
                setColor(bgColor) 
                this.cornerRadius = cornerRadius
            }
            background = bg
        }

        val dragHandle = TextView(this).apply {
            text = "≡"
            textSize = textSizeBtn
            setTextColor(Color.GRAY)
            setPadding(0, 0, (20 * scale).toInt(), 0)
        }
        dockView?.addView(dragHandle)

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

        val settingsBtn = TextView(this).apply {
            text = "⚙️"
            textSize = textSizeBtn
            setPadding((20 * scale).toInt(), 0, 0, 0)
            setOnClickListener {
                val intent = Intent(this@FloatingDockService, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        dockView?.addView(settingsBtn)

        dockWrapper = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(dockView)
        }

        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(dockWrapper, params)
                        return true
                    }
                }
                return false
            }
        })

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
