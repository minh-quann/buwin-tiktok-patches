/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.settings.preference;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.settings.L10n;

@SuppressWarnings("deprecation")
public class SeekBarPreference extends DialogPreference {
    private final IntegerSetting setting;
    private final int minValue;
    private final int maxValue;
    private final String unit;
    private final String baseSummary;
    private int mValue;
    private SeekBar seekBar;
    private TextView valueText;

    public SeekBarPreference(Context context, String title, String summary, IntegerSetting setting) {
        this(context, title, summary, setting, 0, 100, "%");
    }

    public SeekBarPreference(Context context, String title, String summary,
                             IntegerSetting setting, int minValue, int maxValue, String unit) {
        super(context);
        this.setting = setting;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit == null ? "" : unit;
        this.baseSummary = summary;
        setTitle(title);
        setKey(setting.key);
        this.mValue = clamp(setting.savedValue());
        updateSummary();
    }

    private int clamp(int val) {
        return Math.max(minValue, Math.min(maxValue, val));
    }

    private String formatDisplay(int val) {
        if (val == 0) {
            return "0% (Biến mất hoàn toàn)";
        }
        return val + unit;
    }

    private void updateSummary() {
        String base = baseSummary != null ? L10n.t(getContext(), baseSummary) : "";
        String current = L10n.f(getContext(), "Current: %1$s", formatDisplay(mValue));
        String text = base.isEmpty() ? current : base + "\n" + current;
        setSummary(TogglePreference.withRestartNote(getContext(), text, setting));
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        int clamped = clamp(value);
        if (clamped != mValue) {
            mValue = clamped;
            persistInt(clamped);
            setting.save(clamped);
            updateSummary();
            notifyChanged();
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = SettingsUi.dp(context, 22);
        layout.setPadding(padding, padding, padding, SettingsUi.dp(context, 12));

        TextView title = SettingsUi.text(
                context,
                getTitle() == null ? "" : getTitle().toString(),
                SettingsUi.TEXT_HEADLINE,
                SettingsUi.textPrimary(),
                Typeface.BOLD
        );
        SettingsUi.markDialogHeading(title);
        layout.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (baseSummary != null && !baseSummary.isEmpty()) {
            TextView helper = SettingsUi.text(
                    context,
                    L10n.t(context, baseSummary),
                    SettingsUi.TEXT_BODY_SMALL,
                    SettingsUi.textSecondary(),
                    Typeface.NORMAL
            );
            LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            helperParams.setMargins(0, SettingsUi.dp(context, 12), 0, SettingsUi.dp(context, 16));
            layout.addView(helper, helperParams);
        }

        valueText = SettingsUi.text(
                context,
                formatDisplay(mValue),
                SettingsUi.TEXT_HEADLINE,
                SettingsUi.accent(),
                Typeface.BOLD
        );
        valueText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.setMargins(0, SettingsUi.dp(context, 8), 0, SettingsUi.dp(context, 16));
        layout.addView(valueText, valueParams);

        seekBar = new SeekBar(context);
        seekBar.setMax(maxValue - minValue);
        seekBar.setProgress(mValue - minValue);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList accentTint = ColorStateList.valueOf(SettingsUi.accent());
            seekBar.setThumbTintList(accentTint);
            seekBar.setProgressTintList(accentTint);
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int current = minValue + progress;
                if (valueText != null) {
                    valueText.setText(formatDisplay(current));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        seekBarParams.setMargins(0, SettingsUi.dp(context, 4), 0, SettingsUi.dp(context, 12));
        layout.addView(seekBar, seekBarParams);

        return layout;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setPositiveButton(L10n.t(getContext(), "Save"), (dialog, which)
                -> this.onClick(dialog, DialogInterface.BUTTON_POSITIVE));
        builder.setNegativeButton(L10n.t(getContext(), "Cancel"), null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (!positiveResult || seekBar == null) return;
        int newValue = minValue + seekBar.getProgress();
        if (callChangeListener(newValue)) {
            setValue(newValue);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }
}
