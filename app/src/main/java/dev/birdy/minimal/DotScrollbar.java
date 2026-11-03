package dev.birdy.minimal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

// Scroll position as a column of dots: the visible part of the list glows white and fades into its neighbours.
public class DotScrollbar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius, pitch;
    private float start, end; // visible part of the list, as fractions of its length

    public DotScrollbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        radius = 1.5f * density;
        pitch = 9 * density;
        paint.setColor(Color.WHITE);
    }

    // Fed from AbsListView.OnScrollListener; hides itself when everything fits on screen.
    void show(int first, int visible, int total) {
        setVisibility(total > 0 && visible < total ? VISIBLE : INVISIBLE);
        if (total == 0) return;
        start = first / (float) total;
        end = (first + visible) / (float) total;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        int count = (int) (height / pitch) + 1;
        if (count < 2) return;
        float top = getPaddingTop() + (height - (count - 1) * pitch) / 2;
        float x = getWidth() / 2f;
        int firstLit = Math.min(count - 1, (int) (start * count));
        int lastLit = Math.max(firstLit, (int) Math.ceil(end * count) - 1); // at least one dot is lit
        for (int i = 0; i < count; i++) {
            int distance = i < firstLit ? firstLit - i : Math.max(0, i - lastLit);
            float alpha = distance == 0 ? 1f : Math.max(0.16f, 0.55f - 0.13f * distance);
            paint.setAlpha((int) (alpha * 255));
            canvas.drawCircle(x, top + i * pitch, distance == 0 ? radius * 1.4f : radius, paint);
        }
    }
}
