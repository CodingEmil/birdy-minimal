package dev.birdy.minimal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

// The time left in a session: white is what remains, and it drains from left to right, black behind it so it shows
// on any app. Always drawn from the clock, so it is right after leaving and coming back. No animator: those jump to
// the end when the system's animations are off. It redraws only when the edge reaches the next pixel.
final class SessionBar extends View {
    private final Paint remaining = new Paint();
    private final long start, end;

    SessionBar(Context context, long start, long end) {
        super(context);
        this.start = start;
        this.end = end;
        remaining.setColor(Color.WHITE);
    }

    // First pixel of the white part.
    static int edge(int width, long start, long end, long now) {
        if (now <= start) return 0;
        if (now >= end) return width;
        return (int) (width * (now - start) / (end - start));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        long now = Usage.now();
        canvas.drawColor(Color.BLACK);
        canvas.drawRect(edge(width, start, end, now), 0, width, getHeight(), remaining);
        if (now < end) postInvalidateDelayed(Math.max(16, (end - start) / Math.max(1, width)));
    }
}
