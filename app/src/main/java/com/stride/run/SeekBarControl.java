package com.stride.run;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import java.util.function.IntConsumer;

final class SeekBarControl {
    private SeekBarControl() {}

    static void add(Context context, LinearLayout parent, int value, int min, int max, IntConsumer listener) {
        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        parent.addView(seek, new LinearLayout.LayoutParams(-1, (int) (42 * context.getResources().getDisplayMetrics().density)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) listener.accept(min + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }
}
