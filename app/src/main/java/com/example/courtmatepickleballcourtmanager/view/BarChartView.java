package com.example.courtmatepickleballcourtmanager.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.courtmatepickleballcourtmanager.R;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class BarChartView extends View {

    public static class Entry {
        private final int label;
        private final int value;

        public Entry(int label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(ContextCompat.getColor(context, R.color.chart_bar));
        textPaint.setColor(ContextCompat.getColor(context, R.color.chart_text));
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.chart_grid));
        gridPaint.setStrokeWidth(dp(1));
    }

    public void setEntries(List<Entry> data) {
        entries.clear();
        if (data != null) {
            entries.addAll(data);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (entries.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getContext().getString(R.string.stats_no_data),
                    getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        float padLeft = dp(8);
        float padRight = dp(8);
        float padTop = dp(22);
        float padBottom = dp(24);
        float chartBottom = getHeight() - padBottom;
        float chartHeight = chartBottom - padTop;

        int maxValue = 0;
        for (Entry entry : entries) {
            maxValue = Math.max(maxValue, entry.value);
        }
        int scale = Math.max(1, maxValue);

        canvas.drawLine(padLeft, chartBottom, getWidth() - padRight, chartBottom, gridPaint);

        float slot = (getWidth() - padLeft - padRight) / entries.size();
        float barWidth = Math.min(dp(24), slot * 0.62f);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(MoneyUtils.formatShort(maxValue), padLeft, padTop - dp(8), textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float barHeight = chartHeight * entry.value / (float) scale;
            if (entry.value > 0 && barHeight < dp(2)) {
                barHeight = dp(2);
            }
            float left = padLeft + slot * i + (slot - barWidth) / 2f;
            barRect.set(left, chartBottom - barHeight, left + barWidth, chartBottom);
            canvas.drawRoundRect(barRect, dp(3), dp(3), barPaint);

            boolean showLabel = entries.size() <= 15
                    || entry.label == 1
                    || entry.label % 5 == 0
                    || i == entries.size() - 1;
            if (showLabel) {
                canvas.drawText(String.valueOf(entry.label),
                        left + barWidth / 2f, getHeight() - dp(8), textPaint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
