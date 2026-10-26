package com.example.enjoydock

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

class CustomDockLayout(context: Context) : LinearLayout(context) {
    
    var isLocked = false
    
    // Callback'и для связи с сервисом
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Int, Int) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isLocked) return false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                return false 
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (abs(dx) > 30 || abs(dy) > 30) {
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true 
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isLocked) return super.onTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    if (ev.action == MotionEvent.ACTION_MOVE && ev.historySize > 0) {
                        // Плавное движение с учетом истории событий
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        onDrag?.invoke(dx, dy)
                    } else {
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        onDrag?.invoke(dx, dy)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onDragEnd?.invoke() // Сохраняем позицию
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
