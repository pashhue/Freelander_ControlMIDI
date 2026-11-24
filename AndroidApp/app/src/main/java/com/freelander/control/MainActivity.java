package com.freelander.control;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.content.Context;
import android.widget.Toast;
import android.util.SparseIntArray;

public class MainActivity extends Activity {
    private MidiSocketServer midiServer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        midiServer = new MidiSocketServer();
        midiServer.start();
        setContentView(new ControlView(this));
    }

    @Override
    protected void onDestroy() {
        if (midiServer != null) midiServer.stop();
        super.onDestroy();
    }

    private class ControlView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] tabs = {"KEYS", "DRUMS", "TRACKS", "MIXER", "EQ"};
        private int page = 0;
        private float w, h, scale;
        private final SparseIntArray pointerNotes = new SparseIntArray();
        private final SparseIntArray pointerChannels = new SparseIntArray();
        private final int[] mixerValues = new int[8];
        private final int[] panValues = new int[8];
        private final int[] eqValues = new int[7];
        private final int[] blackNotes = {49, 51, 54, 56, 58, 61, 63, 66, 68, 70};
        private final int[] blackAfterWhite = {0, 1, 3, 4, 5, 7, 8, 10, 11, 12};
        private int activeSliderPointer = -1;
        private int activeSliderIndex = -1;
        private int activeSliderType = -1;

        ControlView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            for (int i = 0; i < 8; i++) {
                mixerValues[i] = 100;
                panValues[i] = 64;
            }
            for (int i = 0; i < 7; i++) eqValues[i] = 64;
        }

        private void color(int c) { p.setColor(c); p.setStyle(Paint.Style.FILL); }
        private void text(Canvas c, String s, float x, float y, float size, int col, boolean bold) {
            color(col);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
            c.drawText(s, x, y, p);
        }
        private void round(Canvas c, float l, float t, float r, float b, float radius, int col) {
            color(col); c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            w = getWidth(); h = getHeight(); scale = Math.min(w / 1024f, h / 600f);
            c.save();
            c.scale(w / 1024f, h / 600f);
            c.drawColor(Color.rgb(19, 24, 32));
            drawHeader(c);
            if (page == 0) drawKeyboard(c);
            else if (page == 1) drawDrums(c);
            else if (page == 2) drawTracks(c);
            else if (page == 3) drawMixer(c);
            else drawEq(c);
            c.restore();
        }

        private void drawHeader(Canvas c) {
            round(c, 12, 10, 230, 58, 12, Color.rgb(32, 43, 56));
            text(c, "FREELANDER", 26, 39, 20, Color.WHITE, true);
            text(c, midiServer != null && midiServer.isConnected() ? "USB CONNECTED" : "WAITING FOR USB BRIDGE",
                    250, 37, 13, midiServer != null && midiServer.isConnected() ? Color.rgb(106, 220, 165) : Color.rgb(240, 185, 95), true);
            drawButton(c, 405, 18, 42, 34, "PLAY", Color.rgb(45, 113, 84));
            drawButton(c, 453, 18, 42, 34, "STOP", Color.rgb(82, 91, 105));
            drawButton(c, 501, 18, 42, 34, "REC", Color.rgb(142, 69, 69));
            float tabStart = 555, tabW = 88;
            for (int i = 0; i < tabs.length; i++) {
                float x = tabStart + i * (tabW + 5);
                round(c, x, 10, x + tabW, 58, 10,
                        page == i ? Color.rgb(41, 119, 148) : Color.rgb(39, 47, 59));
                text(c, tabs[i], x + 13, 39, 14, Color.WHITE, true);
            }
        }

        private void drawKeyboard(Canvas c) {
            text(c, "TWO OCTAVES  ·  MIDI 48–71", 25, 95, 14, Color.rgb(175, 190, 205), true);
            float left = 20, top = 125, right = 1004, bottom = 570;
            float keyW = (right - left) / 14f;
            int[] white = {48, 50, 52, 53, 55, 57, 59, 60, 62, 64, 65, 67, 69, 71};
            color(Color.rgb(235, 239, 242));
            for (int i = 0; i < white.length; i++) {
                float x1 = left + i * keyW;
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(x1 + 1, top, x1 + keyW - 1, bottom), 5, 5, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(Color.rgb(120, 132, 145));
                c.drawRoundRect(new RectF(x1 + 1, top, x1 + keyW - 1, bottom), 5, 5, p);
            }
            for (int i = 0; i < blackNotes.length; i++) {
                float x = left + (blackAfterWhite[i] + 1) * keyW - keyW * 0.29f;
                round(c, x, top, x + keyW * 0.58f, top + 270, 5, Color.rgb(28, 33, 41));
                text(c, noteName(blackNotes[i]), x + 4, top + 255, 10, Color.rgb(205, 215, 225), false);
            }
            text(c, "Touch and hold keys to play chords", 25, 594, 12, Color.rgb(145, 158, 173), false);
        }

        private String noteName(int n) {
            String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
            return names[n % 12] + (n / 12 - 1);
        }

        private void drawDrums(Canvas c) {
            text(c, "FPC PADS  ·  MIDI CHANNEL 10  ·  NOTES 36–51", 25, 95, 14, Color.rgb(175, 190, 205), true);
            String[] labels = {"KICK", "SIDE STICK", "SNARE", "CLAP", "ELEC. SNARE", "LOW FLOOR TOM", "CLOSED HAT", "HIGH FLOOR TOM", "PEDAL HAT", "LOW TOM", "OPEN HAT", "LOW-MID TOM", "HI-MID TOM", "CRASH", "HIGH TOM", "RIDE"};
            float left = 20, top = 118, gap = 12, cellW = (984 - gap * 3) / 4f, cellH = 105;
            for (int i = 0; i < 16; i++) {
                int row = i / 4, col = i % 4;
                float x = left + col * (cellW + gap), y = top + row * (cellH + gap);
                round(c, x, y, x + cellW, y + cellH, 12,
                        row % 2 == 0 ? Color.rgb(41, 83, 105) : Color.rgb(50, 61, 78));
                text(c, String.format("%02d", i + 1), x + 16, y + 31, 13, Color.rgb(185, 207, 222), true);
                text(c, labels[i], x + 16, y + 62, 19, Color.WHITE, true);
                text(c, "NOTE " + (36 + i), x + 16, y + 84, 11, Color.rgb(175, 190, 205), false);
            }
        }

        private void drawTracks(Canvas c) {
            text(c, "PLAYLIST TRACKS  ·  FIRST EIGHT LANES", 25, 95, 14, Color.rgb(175, 190, 205), true);
            for (int i = 0; i < 8; i++) {
                float y = 112 + i * 56;
                round(c, 20, y, 1004, y + 48, 8, Color.rgb(34, 43, 55));
                text(c, "TRACK " + (i + 1), 38, y + 30, 15, Color.WHITE, true);
                drawButton(c, 500, y + 7, 145, 34, "MUTE", Color.rgb(140, 77, 75));
                drawButton(c, 665, y + 7, 145, 34, "SOLO", Color.rgb(132, 105, 55));
                drawButton(c, 830, y + 7, 145, 34, "SELECT", Color.rgb(47, 108, 132));
            }
        }

        private void drawButton(Canvas c, float x, float y, float bw, float bh, String label, int col) {
            round(c, x, y, x + bw, y + bh, 7, col);
            color(Color.WHITE);
            p.setTextSize(bw < 60 ? 10 : 13);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, x + bw / 2f, y + bh / 2f + 4f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawMixer(Canvas c) {
            text(c, "MIXER  ·  VOLUME + PAN  ·  CHANNELS 1–8", 25, 95, 14, Color.rgb(175, 190, 205), true);
            float left = 28, cellW = 121;
            for (int i = 0; i < 8; i++) {
                float x = left + i * cellW;
                text(c, "CH " + (i + 1), x + 30, 128, 13, Color.WHITE, true);
                round(c, x + 50, 150, x + 68, 390, 9, Color.rgb(54, 65, 79));
                float fy = 390 - mixerValues[i] / 127f * 225f;
                round(c, x + 29, fy - 11, x + 89, fy + 11, 7, Color.rgb(59, 151, 183));
                text(c, Integer.toString(mixerValues[i]), x + 47, 418, 11, Color.rgb(170, 188, 202), false);
                text(c, "PAN", x + 43, 451, 11, Color.rgb(170, 188, 202), true);
                round(c, x + 12, 465, x + 108, 478, 6, Color.rgb(54, 65, 79));
                float px = x + 12 + panValues[i] / 127f * 96f;
                round(c, px - 5, 459, px + 5, 484, 5, Color.rgb(92, 178, 149));
                text(c, Integer.toString(i + 1), x + 54, 518, 12, Color.rgb(145, 158, 173), false);
            }
        }

        private void drawEq(Canvas c) {
            text(c, "EQUALIZER  ·  CC 100–106  ·  LINK ONCE IN FL STUDIO", 25, 95, 14, Color.rgb(175, 190, 205), true);
            String[] labels = {"LOW", "120", "250", "500", "1K", "4K", "HIGH"};
            float left = 55, cellW = 130;
            for (int i = 0; i < 7; i++) {
                float x = left + i * cellW;
                text(c, labels[i], x + 28, 145, 15, Color.WHITE, true);
                round(c, x + 45, 175, x + 63, 475, 9, Color.rgb(54, 65, 79));
                float fy = 475 - eqValues[i] / 127f * 275f;
                round(c, x + 23, fy - 12, x + 85, fy + 12, 7, Color.rgb(85, 144, 183));
                text(c, Integer.toString(eqValues[i]), x + 46, 507, 12, Color.rgb(170, 188, 202), false);
            }
        }

        private int noteAt(float x, float y) {
            if (y < 125 || y > 570) return -1;
            float left = 20, keyW = 984f / 14f;
            // Black keys are tested first because they overlay the white keys.
            if (y < 395) {
                for (int i = 0; i < blackNotes.length; i++) {
                    float bx = left + (blackAfterWhite[i] + 1) * keyW - keyW * 0.29f;
                    if (x >= bx && x <= bx + keyW * 0.58f) return blackNotes[i];
                }
            }
            int index = (int)((x - left) / keyW);
            int[] white = {48, 50, 52, 53, 55, 57, 59, 60, 62, 64, 65, 67, 69, 71};
            if (index < 0 || index >= white.length) return -1;
            return white[index];
        }

        private void noteOn(int note) { send(0x90, note, 105); }
        private void noteOff(int note) { send(0x80, note, 0); }
        private void send(int status, int d1, int d2) {
            if (midiServer != null) midiServer.send(status, d1, d2);
        }
        private void cc(int number, int value) { send(0xB0, number, Math.max(0, Math.min(127, value))); }

        private void pressAt(int pointer, float x, float y) {
            if (page == 0) {
                int note = noteAt(x, y);
                if (note >= 0) { pointerNotes.put(pointer, note); pointerChannels.put(pointer, 0); noteOn(note); }
            } else if (page == 1) {
                int index = drumAt(x, y);
                if (index >= 0) { pointerNotes.put(pointer, 36 + index); pointerChannels.put(pointer, 9); send(0x99, 36 + index, 112); }
            } else if (page == 2) {
                int[] action = trackActionAt(x, y);
                if (action != null) {
                    cc(action[0], 127); activeSliderPointer = pointer; activeSliderIndex = action[0]; activeSliderType = 10;
                }
            } else if (page == 3) {
                updateMixerAt(x, y);
                activeSliderPointer = pointer;
            } else if (page == 4) {
                updateEqAt(x, y);
                activeSliderPointer = pointer;
            }
        }

        private void moveAt(int pointer, float x, float y) {
            if (page == 0 && pointerNotes.indexOfKey(pointer) >= 0) {
                int previous = pointerNotes.get(pointer);
                int next = noteAt(x, y);
                if (next >= 0 && next != previous) { send(0x80, previous, 0); noteOn(next); pointerNotes.put(pointer, next); }
            } else if (page == 3) updateMixerAt(x, y);
            else if (page == 4) updateEqAt(x, y);
        }

        private void releasePointer(int pointer) {
            int index = pointerNotes.indexOfKey(pointer);
            if (index >= 0) {
                int note = pointerNotes.valueAt(index);
                int channel = pointerChannels.get(pointer, 0);
                send(0x80 | channel, note, 0);
                pointerNotes.removeAt(index);
                pointerChannels.delete(pointer);
            }
            if (activeSliderPointer == pointer) {
                if (activeSliderType == 10) cc(activeSliderIndex, 0);
                activeSliderPointer = -1; activeSliderIndex = -1; activeSliderType = -1;
            }
        }

        private int drumAt(float x, float y) {
            float left = 20, top = 118, gap = 12, cellW = (984 - gap * 3) / 4f, cellH = 105;
            int col = (int)((x - left) / (cellW + gap));
            int row = (int)((y - top) / (cellH + gap));
            if (col < 0 || col > 3 || row < 0 || row > 3) return -1;
            float cellX = left + col * (cellW + gap), cellY = top + row * (cellH + gap);
            if (x > cellX + cellW || y > cellY + cellH) return -1;
            return row * 4 + col;
        }

        private int[] trackActionAt(float x, float y) {
            int row = (int)((y - 112) / 56f);
            if (row < 0 || row >= 8) return null;
            float rowY = 112 + row * 56;
            if (y < rowY || y > rowY + 48) return null;
            if (x >= 500 && x < 645) return new int[]{60 + row};
            if (x >= 665 && x < 810) return new int[]{68 + row};
            if (x >= 830 && x < 975) return new int[]{76 + row};
            return null;
        }

        private void updateMixerAt(float x, float y) {
            if (x < 28 || x >= 28 + 8 * 121) return;
            int index = (int)((x - 28) / 121f);
            if (index < 0 || index >= 8) return;
            float value;
            if (y >= 140 && y <= 405) {
                value = (390f - y) / 225f * 127f;
                mixerValues[index] = clamp((int)value);
                cc(20 + index, mixerValues[index]);
            } else if (y >= 450 && y <= 495) {
                float cellX = 28 + index * 121f;
                value = (x - cellX - 12f) / 96f * 127f;
                panValues[index] = clamp((int)value);
                cc(28 + index, panValues[index]);
            }
            invalidate();
        }

        private void updateEqAt(float x, float y) {
            if (x < 55 || x >= 55 + 7 * 130 || y < 165 || y > 485) return;
            int index = (int)((x - 55) / 130f);
            if (index < 0 || index >= 7) return;
            eqValues[index] = clamp((int)((475f - y) / 275f * 127f));
            cc(100 + index, eqValues[index]);
            invalidate();
        }

        private int clamp(int v) { return Math.max(0, Math.min(127, v)); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            int index = e.getActionIndex();
            float x = e.getX(index) * 1024f / getWidth();
            float y = e.getY(index) * 600f / getHeight();
            int pointer = e.getPointerId(index);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (y < 72) {
                    if (x >= 405 && x < 447) cc(90, 127);
                    else if (x >= 453 && x < 495) cc(91, 127);
                    else if (x >= 501 && x < 543) cc(92, 127);
                    else {
                        float start = 555, tabW = 93;
                        int tab = (int)((x - start) / tabW);
                        if (tab >= 0 && tab < 5) page = tab;
                    }
                    invalidate();
                } else pressAt(pointer, x, y);
                invalidate(); return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    moveAt(e.getPointerId(i), e.getX(i) * 1024f / getWidth(), e.getY(i) * 600f / getHeight());
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                releasePointer(pointer); invalidate(); return true;
            }
            return true;
        }
    }
}
