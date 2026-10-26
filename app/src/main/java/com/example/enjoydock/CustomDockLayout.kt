package com.example.enjoydock

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

class CustomDockLayout(context: Context) : LinearLayout(context) {
    
    var isLocked = false
    var onDrag: ((Int, Int) -> Unit)? = null
    
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isLocked) return false // Если заблокировано, передаем нажатия иконкам

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                return false // Даем иконке получить нажатие
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                // Если сдвинули палец больше чем на 30 пикселей, начинаем перетаскивание
                if (abs(dx) > 30 || abs(dy) > 30) {
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true // Перехватываем управление
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
                    // Вычисляем смещение и передаем его в сервис
                    val dx = (ev.rawX - initialTouchX).toInt()
                    val dy = (ev.rawY - initialTouchY).toInt()
                    onDrag?.invoke(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
