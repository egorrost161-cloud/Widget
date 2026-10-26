package com.example.enjoydock;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class DockScrollView extends HorizontalScrollView {
    public interface OnDragListener {
        void onDrag(int x, int y);
        void onDragEnd();
    }

    private float touchStartX, touchStartY;
    private int startX, startY;
    private boolean isDragging = false;
    private OnDragListener dragListener;
    
    // НОВОЕ: Флаг блокировки
    private boolean isLocked = false;

    public DockScrollView(Context context) {
        super(context);
    }

    public void setDragListener(OnDragListener listener) {
        this.dragListener = listener;
    }

    public void setStartPosition(int x, int y) {
        this.startX = x;
        this.startY = y;
    }

    // НОВЫЙ МЕТОД: Устанавливаем режим
    public void setLocked(boolean locked) {
        this.isLocked = locked;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // ЕСЛИ ЗАБЛОКИРОВАНО: Мы не перехватываем касания.
        // Пусть ScrollView сам плавно прокручивает иконки влево-вправо.
        if (isLocked) {
            return super.onInterceptTouchEvent(ev);
        }

        // ЕСЛИ РАЗБЛОКИРОВАНО: Перехватываем любые движения для перетаскивания окна
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ev.getRawX();
                touchStartY = ev.getRawY();
                isDragging = false;
                return false; // Пропускаем к иконкам
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - touchStartX;
                float dy = ev.getRawY() - touchStartY;
                if (Math.abs(dx) > 20 || Math.abs(dy) > 20) {
                    isDragging = true;
                    return true; // Забираем событие себе (начинаем перетаскивание)
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Если заблокировано — работает обычная прокрутка
        if (isLocked) {
            return super.onTouchEvent(ev);
        }

        // Если разблокировано — работает перетаскивание окна
        if (isDragging) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (dragListener != null) {
                        float dx = ev.getRawX() - touchStartX;
                        float dy = ev.getRawY() - touchStartY;
                        dragListener.onDrag(startX + (int) dx, startY + (int) dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (dragListener != null) dragListener.onDragEnd();
                    return true;
            }
        }
        return super.onTouchEvent(ev);
    }
}
