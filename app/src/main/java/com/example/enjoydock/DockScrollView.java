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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (dragListener == null) return super.onInterceptTouchEvent(ev); // Если заблокировано

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ev.getRawX();
                touchStartY = ev.getRawY();
                isDragging = false;
                return false; // Даем ScrollView или иконкам получить нажатие
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - touchStartX;
                float dy = ev.getRawY() - touchStartY;

                // Проверяем, можно ли прокручивать контент
                boolean canScroll = getChildAt(0) != null && getChildAt(0).getWidth() > getWidth();

                // Перехватываем, если ведем вверх/вниз ИЛИ если нет прокрутки и ведем вбок
                if (Math.abs(dy) > 20 || (!canScroll && Math.abs(dx) > 20)) {
                    isDragging = true;
                    return true; // Перехватываем управление для движения окна
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
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
