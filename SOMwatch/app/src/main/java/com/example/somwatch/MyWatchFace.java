package com.example.somwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import com.example.somwatch.R;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {



        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private Paint mBackgroundPaint;
        private Paint mTextPaint, batteryTextPaint,batteryTextPaint2, batteryDate;
        private boolean touchUp = false;

        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        Bitmap bitmaps[];
        Typeface typeface;
        IntentFilter ifilter;
        Intent batteryStatus;
        int energyColor;
        Paint energyPaint, energyPaintContainer,energyPaintBlack;
        Paint messageGreenPlaceHolder, messagePlaceHolder, borderRect;




        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            energyColor = Color.rgb(103,199,45);


            this.bitmaps = loadAssets();

            typeface = Typeface.createFromAsset(getAssets(), "fonts/smb3.ttf");


            energyPaint = new Paint();
            energyPaint.setStyle(Paint.Style.FILL);
            energyPaint.setColor(energyColor);
            energyPaint.setAntiAlias(true);

            energyPaintContainer = new Paint();
            energyPaintContainer.setStyle(Paint.Style.FILL);
            energyPaintContainer.setColor(Color.WHITE);
            energyPaintContainer.setAntiAlias(true);


            energyPaintBlack = new Paint();
            energyPaintBlack.setStyle(Paint.Style.FILL);
            energyPaintBlack.setColor(Color.BLACK);
            energyPaintBlack.setAntiAlias(true);

            messageGreenPlaceHolder = new Paint();
            messageGreenPlaceHolder.setStyle(Paint.Style.FILL);
            messageGreenPlaceHolder.setColor(Color.rgb(35,107,77));
            messageGreenPlaceHolder.setAntiAlias(true);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.rgb(35,107,77));


            // Initializes Watch Face.
            mTextPaint = new Paint();
            mTextPaint.setTypeface(typeface);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setColor(Color.WHITE);

            batteryTextPaint = new Paint();
            batteryTextPaint.setTypeface(typeface);
            batteryTextPaint.setAntiAlias(true);
            batteryTextPaint.setColor(Color.WHITE);

            batteryTextPaint2 = new Paint();
            batteryTextPaint2.setTypeface(typeface);
            batteryTextPaint2.setAntiAlias(true);
            batteryTextPaint2.setColor(Color.BLACK);

            batteryDate = new Paint();
            batteryDate.setTypeface(typeface);
            batteryDate.setAntiAlias(true);
            batteryDate.setColor(Color.BLACK);

            borderRect = new Paint();
            borderRect.setTypeface(typeface);
            borderRect.setAntiAlias(true);
            borderRect.setColor(Color.rgb(255, 165, 52));

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            float batteryTextSize = resources.getDimension(isRound
                    ? R.dimen.battery_text_size : R.dimen.battery_text_size);

            batteryTextPaint.setTextSize(batteryTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
            }
            updateTimer();
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                    //      .show();

                    touchUp = !touchUp;
                    break;
            }
            invalidate();
        }

        int rightCounter=0;
        //int rowCounter=0;
        int row, right=0;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int size = bounds.height()/ 6;
            int rowUp = bounds.height()/2 - size-10;
            int rowDown = bounds.height()/2-10;

            ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            batteryStatus = getApplicationContext().registerReceiver(null, ifilter);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);


            float batteryPct = (level / (float)scale);

            rightCounter=0;

            Bitmap scaled = Bitmap.createScaledBitmap(bitmaps[0], size, size, true);

            if (isInAmbientMode()) {
                canvas.drawColor(Color.rgb(35,107,77));
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            Bitmap background = Bitmap.createScaledBitmap(bitmaps[5], bounds.width(),  bounds.height(), true);
            canvas.drawBitmap(background, 0,0,mBackgroundPaint);


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            right = 0;

            for(int i=0; i < 12; i++){

                row = ((i<6))?rowUp:rowDown;

                if(i < mCalendar.get(Calendar.HOUR)){
                    scaled = Bitmap.createScaledBitmap(bitmaps[4], size, size, true); // Make sure w and h are in the correct order
                }else if(mCalendar.get(Calendar.MINUTE)>15 && mCalendar.get(Calendar.MINUTE)<=30 && mCalendar.get(Calendar.HOUR) == i){
                    scaled = Bitmap.createScaledBitmap(bitmaps[1], size, size, true); // Make sure w and h are in the correct order
                }else if(mCalendar.get(Calendar.MINUTE)>30 && mCalendar.get(Calendar.MINUTE)<=45 && mCalendar.get(Calendar.HOUR) == i){
                    scaled = Bitmap.createScaledBitmap(bitmaps[2], size, size, true);
                }else if(mCalendar.get(Calendar.MINUTE)>45 && mCalendar.get(Calendar.MINUTE)<60 && mCalendar.get(Calendar.HOUR) == i){
                    scaled = Bitmap.createScaledBitmap(bitmaps[3], size, size, true); // Make sure w and h are in the correct order
                }else{
                    scaled = Bitmap.createScaledBitmap(bitmaps[0], size, size, true); // Make sure w and h are in the correct order
                }
                canvas.drawBitmap(scaled, right,row,mBackgroundPaint);
                if(i!=5)
                    right += ( size + 1);
                else
                    right = 0;
            }

            for(int j=0; j < 60; j++){

                if(j < mCalendar.get(Calendar.MINUTE)) {


                }else if((mCalendar.get(Calendar.SECOND)== 1)||
                        (mCalendar.get(Calendar.SECOND)== 7)||
                        (mCalendar.get(Calendar.SECOND)== 13)||
                        (mCalendar.get(Calendar.SECOND)== 19)||
                        (mCalendar.get(Calendar.SECOND)== 25)||
                        (mCalendar.get(Calendar.SECOND)== 31)||
                        (mCalendar.get(Calendar.SECOND)== 37)||
                        (mCalendar.get(Calendar.SECOND)==  43)||
                        (mCalendar.get(Calendar.SECOND)==  49)||
                        (mCalendar.get(Calendar.SECOND)==  55)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[10], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*6)),(rowDown+size)+10,mBackgroundPaint);
                    scaled = Bitmap.createScaledBitmap(bitmaps[16], size/2, size/2, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*5)),(rowDown+size)+30,mBackgroundPaint);

                }else if((mCalendar.get(Calendar.SECOND)== 2)||
                        (mCalendar.get(Calendar.SECOND)== 8)||
                        (mCalendar.get(Calendar.SECOND)== 14)||
                        (mCalendar.get(Calendar.SECOND)== 20)||
                        (mCalendar.get(Calendar.SECOND)== 26)||
                        (mCalendar.get(Calendar.SECOND)== 32)||
                        (mCalendar.get(Calendar.SECOND)== 38)||
                        (mCalendar.get(Calendar.SECOND)==  44)||
                        (mCalendar.get(Calendar.SECOND)==  50)||
                        (mCalendar.get(Calendar.SECOND)==  56)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[11], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*5)),(rowDown+size)+10,mBackgroundPaint);
                    scaled = Bitmap.createScaledBitmap(bitmaps[17], size/2, size/2, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*4)),(rowDown+size)+30,mBackgroundPaint);


                }else if((mCalendar.get(Calendar.SECOND)== 3)||
                        (mCalendar.get(Calendar.SECOND)== 9)||
                        (mCalendar.get(Calendar.SECOND)== 15)||
                        (mCalendar.get(Calendar.SECOND)== 21)||
                        (mCalendar.get(Calendar.SECOND)== 27)||
                        (mCalendar.get(Calendar.SECOND)== 33)||
                        (mCalendar.get(Calendar.SECOND)== 39)||
                        (mCalendar.get(Calendar.SECOND)==  45)||
                        (mCalendar.get(Calendar.SECOND)==  51)||
                        (mCalendar.get(Calendar.SECOND)==  57)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[12], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*4)),(rowDown+size)+10,mBackgroundPaint);
                    scaled = Bitmap.createScaledBitmap(bitmaps[16], size/2, size/2, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*3)),(rowDown+size)+30,mBackgroundPaint);

                }else if((mCalendar.get(Calendar.SECOND)== 4)||
                        (mCalendar.get(Calendar.SECOND)== 10)||
                        (mCalendar.get(Calendar.SECOND)== 16)||
                        (mCalendar.get(Calendar.SECOND)== 22)||
                        (mCalendar.get(Calendar.SECOND)== 28)||
                        (mCalendar.get(Calendar.SECOND)== 34)||
                        (mCalendar.get(Calendar.SECOND)== 40)||
                        (mCalendar.get(Calendar.SECOND)==  46)||
                        (mCalendar.get(Calendar.SECOND)==  52)||
                        (mCalendar.get(Calendar.SECOND)==  58)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[13], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*3)),(rowDown+size)+10,mBackgroundPaint);
                    scaled = Bitmap.createScaledBitmap(bitmaps[17], size/2, size/2, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*2)),(rowDown+size)+30,mBackgroundPaint);

                }else if((mCalendar.get(Calendar.SECOND)== 5)||
                        (mCalendar.get(Calendar.SECOND)== 11)||
                        (mCalendar.get(Calendar.SECOND)== 17)||
                        (mCalendar.get(Calendar.SECOND)== 23)||
                        (mCalendar.get(Calendar.SECOND)== 29)||
                        (mCalendar.get(Calendar.SECOND)== 35)||
                        (mCalendar.get(Calendar.SECOND)== 41)||
                        (mCalendar.get(Calendar.SECOND)==  47)||
                        (mCalendar.get(Calendar.SECOND)==  53)||
                        (mCalendar.get(Calendar.SECOND)==  59)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[14], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*2)),(rowDown+size)+10,mBackgroundPaint);
                    scaled = Bitmap.createScaledBitmap(bitmaps[16], size/2, size/2, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*1)),(rowDown+size)+30,mBackgroundPaint);

                }else if((mCalendar.get(Calendar.SECOND)== 6)||
                        (mCalendar.get(Calendar.SECOND)== 12)||
                        (mCalendar.get(Calendar.SECOND)== 18)||
                        (mCalendar.get(Calendar.SECOND)== 24)||
                        (mCalendar.get(Calendar.SECOND)== 30)||
                        (mCalendar.get(Calendar.SECOND)== 36)||
                        (mCalendar.get(Calendar.SECOND)== 42)||
                        (mCalendar.get(Calendar.SECOND)==  48)||
                        (mCalendar.get(Calendar.SECOND)==  54)||
                        (mCalendar.get(Calendar.SECOND)==  60)&&
                                mCalendar.get(Calendar.MINUTE) == j){
                    scaled = Bitmap.createScaledBitmap(bitmaps[15], size*1, size*1, true); // Make sure w and h are in the correct order
                    canvas.drawBitmap(scaled, (float) (bounds.width()-(size*1)),(rowDown+size)+10,mBackgroundPaint);
                }

            }




            String text = mAmbient
                    ? String.format("%d:%02d", (mCalendar.get(Calendar.HOUR) == 0)?12:mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%02d:%02d", (mCalendar.get(Calendar.HOUR) == 0)?12:mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));

            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);



            if(touchUp){

                canvas.drawRoundRect(new RectF(size, size, bounds.width()-size, bounds.height()-Math.round(size*3.5)), 7, 7, borderRect);
                canvas.drawRoundRect(new RectF(size+5, size+5, bounds.width()-size-5, bounds.height()-Math.round(size*3.5)-5),7, 7, energyPaintBlack);


                mCalendar = Calendar.getInstance();
                String month = mCalendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
                int day = mCalendar.get(Calendar.DAY_OF_MONTH);
                int year = mCalendar.get(Calendar.YEAR);

                String message = month+", "+day+", "+year;
                canvas.drawText(message, size, size+57, batteryTextPaint);
                canvas.drawText((Math.round(batteryPct*100))+" %", size+110, bounds.width()-(size+200), batteryTextPaint);

            }
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private Bitmap[] loadAssets(){
            Bitmap bitmaps[] = new Bitmap[18];
            Resources res = getResources();
            bitmaps[0]=BitmapFactory.decodeResource(res, R.drawable.blockh0);
            bitmaps[1]=BitmapFactory.decodeResource(res, R.drawable.blockh1);
            bitmaps[2]=BitmapFactory.decodeResource(res, R.drawable.blockh02);
            bitmaps[3]=BitmapFactory.decodeResource(res, R.drawable.blockh3);
            bitmaps[4]=BitmapFactory.decodeResource(res, R.drawable.blockh4);
            bitmaps[5]=BitmapFactory.decodeResource(res, R.drawable.backmario);

            bitmaps[6]=BitmapFactory.decodeResource(res, R.drawable.yoshi1);
            bitmaps[7]=BitmapFactory.decodeResource(res, R.drawable.yoshi2);
            bitmaps[8]=BitmapFactory.decodeResource(res, R.drawable.yoshi3);
            bitmaps[9]=BitmapFactory.decodeResource(res, R.drawable.yoshi4);

            bitmaps[10]=BitmapFactory.decodeResource(res, R.drawable.tannokimario1);
            bitmaps[11]=BitmapFactory.decodeResource(res, R.drawable.tannokimario2);
            bitmaps[12]=BitmapFactory.decodeResource(res, R.drawable.tannokimario3);
            bitmaps[13]=BitmapFactory.decodeResource(res, R.drawable.tannokimario4);
            bitmaps[14]=BitmapFactory.decodeResource(res, R.drawable.tannokimario5);
            bitmaps[15]=BitmapFactory.decodeResource(res, R.drawable.tannokimario6);

            bitmaps[16]=BitmapFactory.decodeResource(res, R.drawable.koopa1);
            bitmaps[17]=BitmapFactory.decodeResource(res, R.drawable.koopa2);


            return bitmaps;
        }
    }
}