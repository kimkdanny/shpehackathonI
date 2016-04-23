package org.boofcv.objecttracking;

/**
 * Created by Kim on 4/23/16.
 */

        import android.content.Context;
        import android.graphics.Canvas;
        import android.graphics.Color;
        import android.graphics.Paint;
        import android.graphics.Path;
        import android.util.AttributeSet;
        import android.view.MotionEvent;
        import android.view.View;

public class TouchEventView extends View {

    int savedCtx;
    private Paint paint = new Paint();
    public Path path = new Path();

    public TouchEventView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setBackgroundColor(Color.argb(0xB0, 0xFF, 0xFF, 0xFF));
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    public Path getPath(){
        return path;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float xPos = event.getX();
        float yPos = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                System.out.println("DOWN " + xPos + " " + yPos);
                path.moveTo(xPos, yPos);
                return true;
            case MotionEvent.ACTION_MOVE:
                System.out.println("MOVE " + xPos + " " + yPos);

                path.lineTo(xPos, yPos);
                break;
            case MotionEvent.ACTION_UP:
                break;
            default:
                return false;

        }
        //repaint
        invalidate();
        return true;
    }
}