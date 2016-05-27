package org.boofcv.objecttracking;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import boofcv.abst.tracker.ConfigComaniciu2003;
import boofcv.abst.tracker.ConfigTld;
import boofcv.abst.tracker.MeanShiftLikelihoodType;
import boofcv.abst.tracker.TrackerObjectQuad;
import boofcv.alg.tracker.sfot.SfotConfig;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoDisplayActivity;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.factory.tracker.FactoryTrackerObjectQuad;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.ImageUInt8;
import boofcv.struct.image.MultiSpectral;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Activity which opens a camera and lets the user select objects to track and switch between
 * different trackers.  The user selects an object by clicking and dragging until the drawn
 * rectangle fills the object.  To select a new object click reset.
 */
public class ObjectTrackerActivity extends VideoDisplayActivity
        implements View.OnTouchListener
{

    int mode = 0;
    Camera cam;
    // size of the minimum square which the user can select
    final static int MINIMUM_MOTION = 20;

    Point2D_I32 click0 = new Point2D_I32();
    Point2D_I32 click1 = new Point2D_I32();
    Dialog dialog;
    Button b;
    TouchEventView touchEvent;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.objecttrack_controls,null);

        LinearLayout parent = getViewContent();
        parent.addView(controls);

        FrameLayout iv = getViewPreview();
        iv.setOnTouchListener(this);
        touchEvent = new TouchEventView(this,null);

        dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setContentView(touchEvent);

        b = (Button) controls.findViewById(R.id.button_reset);
        b.setOnTouchListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // uncomment the line below to visually show the FPS.
        // The FPS is affected by the camera speed and processing power.  The camera speed
        // is often determined by ambient lighting conditions
//        setShowFPS(true);
    }

    @Override
    protected Camera openConfigureCamera( Camera.CameraInfo cameraInfo )
    {
        Camera mCamera = UtilVarious.selectAndOpenCamera(cameraInfo, this);
        cam = mCamera;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();

        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        result = (info.orientation + degrees) % 360;
        result = (360 - result) % 360; // compensate the mirror

        cam.setDisplayOrientation(result);;

        Camera.Parameters param = mCamera.getParameters();
        List<Camera.Size> sizes = param.getSupportedPreviewSizes();
        Camera.Size s = sizes.get(UtilVarious.closest(sizes, 640,480));
        param.setPreviewSize(s.width, s.height);
        mCamera.setParameters(param);
        startObjectTracking();
        return mCamera;
    }


    private void startObjectTracking() {
        TrackerObjectQuad tracker = null;
        ImageType imageType = null;
        imageType = ImageType.single(ImageUInt8.class);
        tracker = FactoryTrackerObjectQuad.tld(new ConfigTld(false), ImageUInt8.class);
        setProcessing(new TrackingProcessing(tracker,imageType) );
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
       if(view.getId() == R.id.button_reset){
           if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
               b.setBackgroundColor(Color.rgb(0x11, 0x11, 0x11));
           }
           if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
               b.setBackgroundColor(Color.rgb(0x44,0x44,0x44));

           }
       }
        else{
            if (mode == 0) {
                if (MotionEvent.ACTION_DOWN == motionEvent.getActionMasked()) {
                    click0.set((int) motionEvent.getX(), (int) motionEvent.getY());
                    click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
                    mode = 1;
                }
            } else if (mode == 1) {
                cam.setDisplayOrientation(180);

                if (MotionEvent.ACTION_MOVE == motionEvent.getActionMasked()) {
                    click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
                } else if (MotionEvent.ACTION_UP == motionEvent.getActionMasked()) {
                    click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
                    mode = 2;

                    dialog.show();
                }
            }
        }
        return true;
    }

    public void resetPressed( View view ) {
        mode = 0;
    }

    protected class TrackingProcessing<T extends ImageBase> extends VideoImageProcessing<MultiSpectral<ImageUInt8>>
    {

        T input;
        ImageType<T> inputType;

        TrackerObjectQuad tracker;
        boolean visible;

        Quadrilateral_F64 location = new Quadrilateral_F64();

        Paint paintSelected = new Paint();
        Paint paintLine0 = new Paint();
        Paint paintLine1 = new Paint();
        Paint paintLine2 = new Paint();
        Paint paintLine3 = new Paint();
        private Paint textPaint = new Paint();

        protected TrackingProcessing(TrackerObjectQuad tracker , ImageType<T> inputType) {
            super(ImageType.ms(3,ImageUInt8.class));
            this.inputType = inputType;

            if( inputType.getFamily() == ImageType.Family.SINGLE_BAND ) {
                input = inputType.createImage(1,1);
            }

            mode = 0;
            this.tracker = tracker;

            paintSelected.setColor(Color.argb(0xFF / 2, 0xFF, 0, 0));

            paintLine0.setColor(Color.RED);
            paintLine0.setStrokeWidth(3f);
            paintLine1.setColor(Color.MAGENTA);
            paintLine1.setStrokeWidth(3f);
            paintLine2.setColor(Color.BLUE);
            paintLine2.setStrokeWidth(3f);
            paintLine3.setColor(Color.GREEN);
            paintLine3.setStrokeWidth(3f);

            // Create out paint to use for drawing
            textPaint.setARGB(255, 200, 0, 0);
            textPaint.setTextSize(60);

        }

        @Override
        protected void process(MultiSpectral<ImageUInt8> input, Bitmap output, byte[] storage)
        {
            updateTracker(input);
            visualize(input, output, storage);
        }

        private void updateTracker(MultiSpectral<ImageUInt8> color) {
            if( inputType.getFamily() == ImageType.Family.SINGLE_BAND ) {
                input.reshape(color.width,color.height);
                ConvertImage.average(color, (ImageUInt8) input);
            } else {
                input = (T)color;
            }

            if( mode == 2 ) {
                imageToOutput(click0.x, click0.y, location.a);
                imageToOutput(click1.x, click1.y, location.c);

                // make sure the user selected a valid region
                makeInBounds(location.a);
                makeInBounds(location.c);
                double xAvg = (location.a.getX() + location.b.getX() + location.c.getX() + location.d.getX())/4;
                double yAvg = (location.a.getY() + location.b.getY() + location.c.getY() + location.d.getY())/4;
                touchEvent.getPath().moveTo((float) (xAvg*2), (float) (yAvg*1.6));

                System.out.println("WHY HERE");

                if( movedSignificantly(location.a,location.c) ) {
                    // use the selected region and start the tracker
                    location.b.set(location.c.x, location.a.y);
                    location.d.set( location.a.x, location.c.y );

                    tracker.initialize(input, location);
                    visible = true;
                    mode = 3;
                } else {
                    // the user screw up. Let them know what they did wrong
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ObjectTrackerActivity.this, "Drag a larger region", Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                        }
                    });
                    mode = 0;
                }
            } else if( mode == 3 ) {
                double xAvg = (location.a.getX() + location.b.getX() + location.c.getX() + location.d.getX())/4;
                double yAvg = (location.a.getY() + location.b.getY() + location.c.getY() + location.d.getY())/4;
              //  System.out.println(xAvg + " " + yAvg);

                touchEvent.getPath().lineTo((float) xAvg*2, (float) (yAvg*1.6));
                touchEvent.postInvalidate();

                visible = tracker.process(input,location);
            }
        }

        private void visualize(MultiSpectral<ImageUInt8> color, Bitmap output, byte[] storage) {
            ConvertBitmap.multiToBitmap(color, output, storage);
            Canvas canvas = new Canvas(output);

            if( mode == 1 ) {
                Point2D_F64 a = new Point2D_F64();
                Point2D_F64 b = new Point2D_F64();

                imageToOutput(click0.x, click0.y, a);
                imageToOutput(click1.x, click1.y, b);

                canvas.drawRect((int)a.x,(int)a.y,(int)b.x,(int)b.y,paintSelected);
            } else if( mode >= 2 ) {
                if( visible ) {
                    Quadrilateral_F64 q = location;

                    drawLine(canvas,q.a,q.b,paintLine0);
                    drawLine(canvas,q.b,q.c,paintLine1);
                    drawLine(canvas,q.c,q.d,paintLine2);
                    drawLine(canvas,q.d,q.a,paintLine3);
                } else {
                    canvas.drawText("?",color.width/2,color.height/2,textPaint);
                }
            }
        }

        private void drawLine( Canvas canvas , Point2D_F64 a , Point2D_F64 b , Paint color ) {
            canvas.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y,color);
        }

        private void makeInBounds( Point2D_F64 p ) {
            if( p.x < 0 ) p.x = 0;
            else if( p.x >= input.width )
                p.x = input.width - 1;

            if( p.y < 0 ) p.y = 0;
            else if( p.y >= input.height )
                p.y = input.height - 1;

        }

        private boolean movedSignificantly( Point2D_F64 a , Point2D_F64 b ) {
            if( Math.abs(a.x-b.x) < MINIMUM_MOTION )
                return false;
            if( Math.abs(a.y-b.y) < MINIMUM_MOTION )
                return false;

            return true;
        }
    }
}
