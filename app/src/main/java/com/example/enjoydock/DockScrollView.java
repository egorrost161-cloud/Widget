package com.example.enjoydock;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class DockScrollView extends HorizontalScrollView {
    public interface OnDragListener {
        void onDragStart();
        void onDrag(float dx, float dy);
        void onDragEnd();
    }

    private float touchStartX, touchStartY;
    private boolean isDragging = false;
    private OnDragListener dragListener;
    private boolean isLocked = false;

    public DockScrollView(Context context) {
        super(context);
    }

    public void setDragListener(OnDragListener listener) {
        this.dragListener = listener;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Если заблокировано - работает обычная прокрутка иконок
        if (isLocked) {
            return super.onInterceptTouchEvent(ev);
        }

        // Если разблокировано - мы перехватываем всё управление для перетаскивания
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ev.getRawX();
                touchStartY = ev.getRawY();
                isDragging = false;
                if (dragListener != null) dragListener.onDragStart();
                return false; // Даем иконке получить клик
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getRawX() - touchStartX);
                float dy = Math.abs(ev.getRawY() - touchStartY);
                if (dx > 10 || dy > 10) {
                    isDragging = true;
                    return true; // Начинаем перетаскивание
                }
                break;
        }
        return false; // ВАЖНО: не даем HorizontalScrollView перехватить свайп
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isLocked) {
            return super.onTouchEvent(ev);
        }

        if (isDragging) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (dragListener != null) {
                        // Передаем разницу (насколько сдвинулся палец)
                        dragListener.onDrag(ev.getRawX() - touchStartX, ev.getRawY() - touchStartY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (dragListener != null) dragListener.onDragEnd();
                    return true;
            }
        }
        return false;
    }
}
