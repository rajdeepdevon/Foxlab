package com.CodeBySonu.VoxSherpa;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public final class SupertonicLanguagePickerFragment extends BottomSheetDialogFragment {
    public static final String RESULT_KEY = "supertonic_language_selection";
    public static final String LANGUAGE_KEY = "language_code";
    private static final String ARG_SELECTED_LANGUAGE = "selected_language";

    private static final HashMap<String, String> FLAGS = createFlags();

    public static SupertonicLanguagePickerFragment newInstance(String selectedLanguage) {
        SupertonicLanguagePickerFragment fragment = new SupertonicLanguagePickerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED_LANGUAGE, SupertonicEngine.normalizeLanguage(selectedLanguage));
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setContentView(createContent());
        dialog.setOnShowListener(ignored -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    private View createContent() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, dp(8), padding, dp(16));
        content.setBackgroundColor(Color.rgb(7, 11, 20));

        View handle = new View(requireContext());
        handle.setBackgroundColor(Color.rgb(63, 75, 97));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(18);
        content.addView(handle, handleParams);

        TextView title = new TextView(requireContext());
        title.setText("Choose language");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Select a language for Supertonic V3");
        subtitle.setTextColor(Color.rgb(148, 163, 184));
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(12);
        content.addView(subtitle, subtitleParams);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        LinearLayout languages = new LinearLayout(requireContext());
        languages.setOrientation(LinearLayout.VERTICAL);

        ArrayList<String> languageCodes = new ArrayList<>();
        Collections.addAll(languageCodes, SupertonicEngine.getSupportedLanguages());
        Collections.sort(languageCodes, Comparator.comparing(this::getLanguageName));
        String selectedLanguage = getArguments() == null ? "en"
                : SupertonicEngine.normalizeLanguage(getArguments().getString(ARG_SELECTED_LANGUAGE, "en"));
        for (String languageCode : languageCodes) {
            languages.addView(createLanguageRow(languageCode, selectedLanguage));
        }
        scrollView.addView(languages);
        int listHeight = Math.min(dp(420), getResources().getDisplayMetrics().heightPixels * 3 / 5);
        content.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        return content;
    }

    private View createLanguageRow(String languageCode, String selectedLanguage) {
        boolean selected = languageCode.equals(selectedLanguage);
        MaterialCardView row = new MaterialCardView(requireContext());
        row.setCardBackgroundColor(selected ? Color.rgb(22, 42, 58) : Color.rgb(18, 24, 38));
        row.setStrokeColor(selected ? Color.rgb(0, 229, 255) : Color.rgb(26, 34, 52));
        row.setStrokeWidth(dp(selected ? 1 : 0));
        row.setRadius(dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setForeground(requireContext().getDrawable(android.R.drawable.list_selector_background));

        LinearLayout labelContainer = new LinearLayout(requireContext());
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);
        labelContainer.setPadding(dp(16), 0, dp(16), 0);
        TextView label = new TextView(requireContext());
        label.setText(getFlag(languageCode) + "  " + getLanguageName(languageCode));
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        labelContainer.addView(label, new LinearLayout.LayoutParams(0, dp(54), 1));
        if (selected) {
            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setTextColor(Color.rgb(0, 229, 255));
            check.setTextSize(20);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labelContainer.addView(check);
        }
        row.addView(labelContainer);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);
        row.setOnClickListener(view -> {
            Bundle result = new Bundle();
            result.putString(LANGUAGE_KEY, languageCode);
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            dismiss();
        });
        return row;
    }

    private String getLanguageName(String languageCode) {
        String name = new Locale(languageCode).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? languageCode.toUpperCase(Locale.ROOT) : name;
    }

    public static String getLanguageFlag(String languageCode) {
        String flag = FLAGS.get(languageCode);
        return flag == null ? "🌐" : flag;
    }

    private String getFlag(String languageCode) {
        return getLanguageFlag(languageCode);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static HashMap<String, String> createFlags() {
        HashMap<String, String> flags = new HashMap<>();
        flags.put("en", "🇺🇸");
        flags.put("ko", "🇰🇷");
        flags.put("ja", "🇯🇵");
        flags.put("ar", "🇸🇦");
        flags.put("bg", "🇧🇬");
        flags.put("cs", "🇨🇿");
        flags.put("da", "🇩🇰");
        flags.put("de", "🇩🇪");
        flags.put("el", "🇬🇷");
        flags.put("es", "🇪🇸");
        flags.put("et", "🇪🇪");
        flags.put("fi", "🇫🇮");
        flags.put("fr", "🇫🇷");
        flags.put("hi", "🇮🇳");
        flags.put("hr", "🇭🇷");
        flags.put("hu", "🇭🇺");
        flags.put("id", "🇮🇩");
        flags.put("it", "🇮🇹");
        flags.put("lt", "🇱🇹");
        flags.put("lv", "🇱🇻");
        flags.put("nl", "🇳🇱");
        flags.put("pl", "🇵🇱");
        flags.put("pt", "🇵🇹");
        flags.put("ro", "🇷🇴");
        flags.put("ru", "🇷🇺");
        flags.put("sk", "🇸🇰");
        flags.put("sl", "🇸🇮");
        flags.put("sv", "🇸🇪");
        flags.put("tr", "🇹🇷");
        flags.put("uk", "🇺🇦");
        flags.put("vi", "🇻🇳");
        return flags;
    }
}
