package com.example.translator.ui.camera;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.example.translator.R;

public class CropOverlayView extends View {

    private Paint paint;
    private Paint fillPaint;
    private Paint overlayPaint;

    private RectF cropRect = new RectF();
    private boolean isDragging = false;
    private DragHandle dragHandle = DragHandle.NONE;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;

    private static final float HANDLE_RADIUS = 30f;
    private static final float MIN_CROP_SIZE = 100f;

    public enum DragHandle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    public CropOverlayView(Context context) {
        super(context);
        init(context);
    }

    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CropOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(ContextCompat.getColor(context, R.color.primary_color));

        fillPaint = new Paint();
        fillPaint.setAntiAlias(true);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(100, 33, 150, 243)); // Semi-transparent blue

        overlayPaint = new Paint();
        overlayPaint.setAntiAlias(true);
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.argb(120, 0, 0, 0)); // Semi-transparent black
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Initialize crop rect to center 70% of the view
        float margin = Math.min(w, h) * 0.15f;
        cropRect.set(margin, margin, w - margin, h - margin);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // Draw overlay outside crop area
        canvas.drawRect(0f, 0f, w, cropRect.top, overlayPaint);
        canvas.drawRect(0f, cropRect.bottom, w, h, overlayPaint);
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, overlayPaint);

        // Draw crop rectangle
        canvas.drawRect(cropRect, paint);

        // Draw corner handles
        drawHandle(canvas, cropRect.left, cropRect.top);
        drawHandle(canvas, cropRect.right, cropRect.top);
        drawHandle(canvas, cropRect.left, cropRect.bottom);
        drawHandle(canvas, cropRect.right, cropRect.bottom);

        // Draw grid lines
        drawGridLines(canvas);
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, HANDLE_RADIUS, fillPaint);
        canvas.drawCircle(x, y, HANDLE_RADIUS, paint);
    }

    private void drawGridLines(Canvas canvas) {
        float thirdWidth = cropRect.width() / 3;
        float thirdHeight = cropRect.height() / 3;

        // Vertical lines
        canvas.drawLine(
                cropRect.left + thirdWidth, cropRect.top,
                cropRect.left + thirdWidth, cropRect.bottom,
                paint
        );
        canvas.drawLine(
                cropRect.left + 2 * thirdWidth, cropRect.top,
                cropRect.left + 2 * thirdWidth, cropRect.bottom,
                paint
        );

        // Horizontal lines
        canvas.drawLine(
                cropRect.left, cropRect.top + thirdHeight,
                cropRect.right, cropRect.top + thirdHeight,
                paint
        );
        canvas.drawLine(
                cropRect.left, cropRect.top + 2 * thirdHeight,
                cropRect.right, cropRect.top + 2 * thirdHeight,
                paint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                dragHandle = getHandleAt(event.getX(), event.getY());
                isDragging = dragHandle != DragHandle.NONE;
                return isDragging;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float deltaX = event.getX() - lastTouchX;
                    float deltaY = event.getY() - lastTouchY;

                    switch (dragHandle) {
                        case TOP_LEFT:
                            cropRect.left = Math.min(cropRect.left + deltaX, cropRect.right - MIN_CROP_SIZE);
                            cropRect.top = Math.min(cropRect.top + deltaY, cropRect.bottom - MIN_CROP_SIZE);
                            break;
                        case TOP_RIGHT:
                            cropRect.right = Math.max(cropRect.right + deltaX, cropRect.left + MIN_CROP_SIZE);
                            cropRect.top = Math.min(cropRect.top + deltaY, cropRect.bottom - MIN_CROP_SIZE);
                            break;
                        case BOTTOM_LEFT:
                            cropRect.left = Math.min(cropRect.left + deltaX, cropRect.right - MIN_CROP_SIZE);
                            cropRect.bottom = Math.max(cropRect.bottom + deltaY, cropRect.top + MIN_CROP_SIZE);
                            break;
                        case BOTTOM_RIGHT:
                            cropRect.right = Math.max(cropRect.right + deltaX, cropRect.left + MIN_CROP_SIZE);
                            cropRect.bottom = Math.max(cropRect.bottom + deltaY, cropRect.top + MIN_CROP_SIZE);
                            break;
                        case CENTER:
                            float newLeft = cropRect.left + deltaX;
                            float newTop = cropRect.top + deltaY;
                            float newRight = cropRect.right + deltaX;
                            float newBottom = cropRect.bottom + deltaY;

                            // Keep crop rect within bounds
                            if (newLeft >= 0 && newRight <= getWidth() && newTop >= 0 && newBottom <= getHeight()) {
                                cropRect.offset(deltaX, deltaY);
                            }
                            break;
                    }

                    // Keep crop rect within view bounds
                    cropRect.left = Math.max(cropRect.left, 0f);
                    cropRect.top = Math.max(cropRect.top, 0f);
                    cropRect.right = Math.min(cropRect.right, getWidth());
                    cropRect.bottom = Math.min(cropRect.bottom, getHeight());

                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                dragHandle = DragHandle.NONE;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private DragHandle getHandleAt(float x, float y) {
        float tolerance = HANDLE_RADIUS * 1.5f;

        // Check corner handles first
        if (isPointNear(x, y, cropRect.left, cropRect.top, tolerance)) {
            return DragHandle.TOP_LEFT;
        }
        if (isPointNear(x, y, cropRect.right, cropRect.top, tolerance)) {
            return DragHandle.TOP_RIGHT;
        }
        if (isPointNear(x, y, cropRect.left, cropRect.bottom, tolerance)) {
            return DragHandle.BOTTOM_LEFT;
        }
        if (isPointNear(x, y, cropRect.right, cropRect.bottom, tolerance)) {
            return DragHandle.BOTTOM_RIGHT;
        }

        // Check if touch is inside crop area for moving
        if (cropRect.contains(x, y)) {
            return DragHandle.CENTER;
        }

        return DragHandle.NONE;
    }

    private boolean isPointNear(float x, float y, float targetX, float targetY, float tolerance) {
        float dx = x - targetX;
        float dy = y - targetY;
        return dx * dx + dy * dy <= tolerance * tolerance;
    }

    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    public void setCropRect(RectF rect) {
        cropRect.set(rect);
        invalidate();
    }
}