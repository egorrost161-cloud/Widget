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

    public DockScrollView(Context context) {
        super(context);
    }

    public void setDragListener(OnDragListener listener) {
        this.dragListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ev.getRawX();
                touchStartY = ev.getRawY();
                isDragging = false;
                if (dragListener != null) dragListener.onDragStart();
                return false; // Даем нажатию дойти до иконок

            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - touchStartX;
                float dy = ev.getRawY() - touchStartY;

                // Если контент не помещается по ширине, разрешаем скролл влево-вправо
                boolean canScroll = getChildAt(0) != null && getChildAt(0).getWidth() > getWidth();

                if (!isDragging) {
                    // Если ведем вверх/вниз (больше 15 пикселей) ИЛИ контент помещается и мы ведем в любую сторону
                    if (Math.abs(dy) > 15 || (!canScroll && Math.abs(dx) > 15)) {
                        isDragging = true;
                        return true; // Перехватываем управление для перетаскивания окна
                    }
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
                        dragListener.onDrag(ev.getRawX() - touchStartX, ev.getRawY() - touchStartY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (dragListener != null) dragListener.onDragEnd();
                    return true;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
