/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.rendering.TextRenderer;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private static final float HAND_END_CAP_RADIUS = 4f;
        private static final float SHADOW_RADIUS = 0f;


        private Bitmap mBackgroundBitmap;

        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float STROKE_WIDTH = 3f;

        private Calendar mCalendar;

        private Paint mBackgroundPaint;
        private Paint mHandPaint;
        private Paint mSecondHandPaint;
        private Paint mMinuteTickPaint;

        private TextPaint mWhiteTextPaint;

        private TextRenderer mWhiteTextRenderer;

        private boolean mAmbient;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.custom_background);

            mMinuteTickPaint = new Paint();
            mMinuteTickPaint.setColor(Color.WHITE);
            mMinuteTickPaint.setStrokeWidth(1);

            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(7);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.SQUARE);
            mHandPaint.setStyle(Paint.Style.FILL);

            mSecondHandPaint = new Paint();
            mSecondHandPaint.setColor(Color.RED);
            mSecondHandPaint.setStrokeWidth(3);
            mSecondHandPaint.setAntiAlias(true);
            mSecondHandPaint.setStrokeCap(Paint.Cap.SQUARE);
            mSecondHandPaint.setStyle(Paint.Style.FILL);

            mWhiteTextPaint = new TextPaint();
            mWhiteTextPaint.setTextSize(22);
            mWhiteTextPaint.setFontFeatureSettings("verdana");
            mWhiteTextPaint.setColor(0x7f0f0024);

            mWhiteTextRenderer = new TextRenderer();
            mWhiteTextRenderer.setPaint(mWhiteTextPaint);



            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            if (inAmbientMode) {
                mHandPaint.setAntiAlias(false);
            } else {
                mHandPaint.setAntiAlias(true);
            }

            invalidate();

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mHourHandLength = 0.5f * mWidth / 2;
            mMinuteHandLength = 0.7f * mWidth / 2;
            mSecondHandLength = 0.9f * mWidth / 2;

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale),
                    true);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);

            canvas.drawText("3", mWidth - 20, mCenterY, mWhiteTextPaint);
            canvas.drawText("6", mCenterX, -(mHeight) + 20, mWhiteTextPaint);
            canvas.drawText("9", -(mWidth) + 20, mCenterY, mWhiteTextPaint);
            canvas.drawText("12", mCenterX, mHeight - 20, mWhiteTextPaint);
            canvas.drawText("12", mCenterX, mCenterY, mWhiteTextPaint);

            mWhiteTextRenderer.setText("12");
            mWhiteTextRenderer.draw(canvas, new Rect(100, 100, 150, 150));

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            // save the canvas state before we begin to rotate it
            canvas.save();

            // make minute and hour tick marks


            for(int i = 0; i < 12; i++)
            {
                canvas.drawLine(mCenterX, mHeight - 16, mCenterX, mHeight, mHandPaint);
                canvas.rotate(6f, mCenterX, mCenterY);
                for(int j = 0; j < 4; j++)
                {
                    canvas.drawLine(mCenterX, mHeight - 3, mCenterX, mHeight, mMinuteTickPaint);
                    canvas.rotate(6f, mCenterX, mCenterY);
                }
            }

            // draw hands
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mHourHandLength, mHandPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mMinuteHandLength, mHandPaint);

            canvas.drawCircle(mCenterX, mCenterY, 13, mHandPaint);
            canvas.drawCircle(mCenterX, mCenterY, 7, mBackgroundPaint);

            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mSecondHandLength,
                        mSecondHandPaint);
                canvas.drawCircle(mCenterX, mCenterY, 8, mSecondHandPaint);
                canvas.drawCircle(mCenterX, mCenterY, 4, mBackgroundPaint);

            }

            // restore the canvas' original orientation.
            canvas.restore();
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

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
