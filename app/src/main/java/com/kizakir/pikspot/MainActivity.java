package com.kizakir.pikspot;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String CHROME_PACKAGE = "com.android.chrome";
    private static final String CHROME_DISPATCHER = "com.google.android.apps.chrome.IntentDispatcher";
    private static final String CHROME_TABBED_ACTIVITY = "org.chromium.chrome.browser.ChromeTabbedActivity";
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";
    private static final String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String PREFS_NAME = "settings";
    private static final String PREF_COPY_ENABLED = "coordinate_copy_enabled";
    private static final String PREF_HISTORY = "coordinate_history";
    private static final String PREF_PINNED_HISTORY = "pinned_history";
    private static final String PREF_LAST_COORDINATE = "last_coordinate";
    private static final String PREF_LAST_SHORT_LINK_TIME = "last_short_link_time";
    private static final int HISTORY_LIMIT = 99;
    private static final long SHORT_LINK_GUARD_MS = 8000;
    private static final SimpleDateFormat HISTORY_TIME_FORMAT =
            new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
    private static final int COLOR_BACKGROUND = Color.rgb(244, 241, 236);
    private static final int COLOR_CARD = Color.rgb(255, 254, 251);
    private static final int COLOR_TEXT = Color.rgb(42, 47, 47);
    private static final int COLOR_MUTED = Color.rgb(133, 132, 125);
    private static final int COLOR_PRIMARY = Color.rgb(105, 132, 118);
    private static final int COLOR_SECONDARY = Color.rgb(152, 171, 184);
    private static final int COLOR_SURFACE = Color.rgb(235, 231, 224);
    private static final int COLOR_HISTORY = Color.rgb(228, 190, 184);
    private static final int COLOR_DANGER = Color.rgb(130, 83, 78);
    private static final Pattern COORDINATE_PATTERN = Pattern.compile(
            "(-?\\d{1,2}\\.\\d{4,})\\s*,\\s*(-?\\d{1,3}\\.\\d{4,})"
    );

    private TextView coordinateView;
    private TextView rawView;
    private WebView webView;
    private Switch enableCopySwitch;
    private String coordinateText = "";
    private String rawText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), getStatusBarHeight() + dp(14), dp(18), dp(12));
        header.setBackgroundColor(COLOR_BACKGROUND);

        ImageView title = new ImageView(this);
        title.setImageResource(R.drawable.pikspot_logo);
        title.setAdjustViewBounds(true);
        title.setScaleType(ImageView.ScaleType.FIT_CENTER);
        title.setOnLongClickListener(v -> {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (v.isPressed()) {
                    showRawDataDialog();
                }
            }, 3000);
            return true;
        });
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(58));
        header.addView(title, titleParams);

        TextView subtitle = label(getString(R.string.app_subtitle), 14, COLOR_MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        addWithMargins(header, subtitle, 0, dp(4), 0, 0);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(22));
        page.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout setupCard = card();
        TextView setupTitle = label(getString(R.string.setup_title), 18, COLOR_TEXT, true);
        addWithMargins(setupCard, setupTitle, 0, 0, 0, dp(10));

        Button openBrowserSettings = button(getString(R.string.open_browser_settings), true);
        openBrowserSettings.setOnClickListener(v -> openDefaultBrowserSettings());
        addWithMargins(setupCard, actionRow(
                openBrowserSettings,
                getString(R.string.open_browser_settings),
                getString(R.string.browser_settings_hint)
        ), 0, 0, 0, dp(10));

        Button openMapsSettings = button(getString(R.string.open_maps_settings), false);
        openMapsSettings.setOnClickListener(v -> openMapsSettings());
        addWithMargins(setupCard, actionRow(
                openMapsSettings,
                getString(R.string.open_maps_settings),
                getString(R.string.maps_settings_hint)
        ), 0, 0, 0, dp(12));

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setPadding(0, dp(8), 0, dp(2));

        TextView switchLabel = label(getString(R.string.enable_coordinate_copy), 16, COLOR_TEXT, true);
        switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0, -2, 1));

        enableCopySwitch = new Switch(this);
        enableCopySwitch.setText("");
        enableCopySwitch.setChecked(isCoordinateCopyEnabled());
        enableCopySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                getPreferences().edit().putBoolean(PREF_COPY_ENABLED, isChecked).apply()
        );
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-2, -2);
        switchParams.setMargins(0, 0, dp(10), 0);
        switchRow.addView(enableCopySwitch, switchParams);

        TextView hintButton = infoButton();
        hintButton.setOnClickListener(v -> showInfoDialog(
                getString(R.string.enable_coordinate_copy),
                getString(R.string.copy_mode_hint)
        ));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        switchRow.addView(hintButton, hintParams);
        setupCard.addView(switchRow, new LinearLayout.LayoutParams(-1, -2));
        addWithMargins(content, setupCard, 0, 0, 0, dp(14));

        LinearLayout coordinateCard = card();
        TextView coordinateTitle = label(getString(R.string.latest_coordinate), 18, COLOR_TEXT, true);
        addWithMargins(coordinateCard, coordinateTitle, 0, 0, 0, dp(8));
        coordinateView = new TextView(this);
        coordinateView.setTextSize(22);
        coordinateView.setTextColor(COLOR_TEXT);
        coordinateView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        coordinateView.setGravity(Gravity.CENTER);
        coordinateView.setPadding(dp(12), dp(16), dp(12), dp(16));
        coordinateView.setBackground(rounded(COLOR_SURFACE, dp(16)));
        coordinateView.setTextIsSelectable(true);
        coordinateText = getPreferences().getString(PREF_LAST_COORDINATE, "");
        coordinateView.setText(coordinateText.isEmpty()
                ? getString(R.string.no_coordinate)
                : coordinateText);
        addWithMargins(coordinateCard, coordinateView, 0, 0, 0, dp(12));

        Button copyCoordinate = button(getString(R.string.copy_coordinate), true);
        copyCoordinate.setOnClickListener(v -> copyText("coordinate", coordinateText));
        addWithMargins(coordinateCard, copyCoordinate, 0, 0, 0, dp(10));

        Button historyButton = button(getString(R.string.coordinate_history), false);
        historyButton.setOnClickListener(v -> showCoordinateHistory());
        coordinateCard.addView(historyButton, new LinearLayout.LayoutParams(-1, dp(50)));
        addWithMargins(content, coordinateCard, 0, 0, 0, dp(14));

        TextView credit = label(getString(R.string.credit_text), 12, COLOR_MUTED, false);
        credit.setGravity(Gravity.CENTER);
        credit.setAlpha(0.72f);
        addWithMargins(content, credit, 0, dp(4), 0, dp(8));

        rawView = new TextView(this);
        rawView.setTextSize(12);
        rawView.setTextColor(COLOR_MUTED);
        rawView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        rawView.setPadding(dp(12), dp(12), dp(12), dp(12));
        rawView.setBackground(rounded(Color.rgb(248, 246, 241), dp(14)));
        rawView.setTextIsSelectable(true);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0));

        return root;
    }

    private TextView label(String text, int sizeSp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(rounded(COLOR_CARD, dp(22)));
        return layout;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setAllCaps(false);
        button.setBackground(rounded(primary ? COLOR_PRIMARY : COLOR_SECONDARY, dp(16)));
        button.setMinHeight(dp(50));
        return button;
    }

    private LinearLayout actionRow(Button button, String title, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView hintButton = infoButton();
        hintButton.setOnClickListener(v -> showInfoDialog(title, hint));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        hintParams.setMargins(dp(10), 0, 0, 0);
        row.addView(hintButton, hintParams);
        return row;
    }

    private TextView infoButton() {
        return infoButton(32);
    }

    private TextView infoButton(int sizeDp) {
        TextView hintButton = label("i", 14, COLOR_MUTED, true);
        hintButton.setGravity(Gravity.CENTER);
        hintButton.setBackground(outlined(COLOR_SURFACE, COLOR_MUTED, dp(sizeDp / 2)));
        return hintButton;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable outlined(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = rounded(fillColor, radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void addWithMargins(LinearLayout parent, View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(left, top, right, bottom);
        parent.addView(view, params);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        String dataText = data == null ? "" : data.toString();
        String action = intent.getAction();
        boolean hasSharedText = intent.getStringExtra(Intent.EXTRA_TEXT) != null;
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action) && !hasSharedText) {
            return;
        }

        if (isMapsShortLink(data)) {
            getPreferences().edit()
                    .putLong(PREF_LAST_SHORT_LINK_TIME, System.currentTimeMillis())
                    .apply();
            if (!openInChrome(intent, data)) {
                maybeOpenAsBrowser(intent, dataText);
            }
            return;
        }

        if (!isCoordinateCopyEnabled() && openInChrome(intent, data)) {
            return;
        }

        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        String combinedData = dataText + "\n" + (sharedText == null ? "" : sharedText);
        String decodedData = decode(combinedData);

        coordinateText = findCoordinate(decodedData);
        rawText = buildRawText(intent, dataText, sharedText, decodedData);
        if (!coordinateText.isEmpty() && isExternalViewLink(intent, data)
                && (isWithinShortLinkGuard() || isExcludedLinkSource(intent))) {
            coordinateText = "";
            coordinateView.setText(getLastCoordinateOrPlaceholder());
            if (!openInChrome(intent, data)) {
                maybeOpenAsBrowser(intent, dataText);
            }
            rawView.setText(rawText);
            return;
        }
        if (!coordinateText.isEmpty() && isExternalViewLink(intent, data)
                && shouldIgnoreCoordinateLink(data, decodedData)) {
            coordinateText = "";
            coordinateView.setText(getLastCoordinateOrPlaceholder());
            if (!openInChrome(intent, data)) {
                maybeOpenAsBrowser(intent, dataText);
            }
            rawView.setText(rawText);
            return;
        }

        if (coordinateText.isEmpty()) {
            coordinateView.setText(getLastCoordinateOrPlaceholder());
            if (!openInChrome(intent, data)) {
                maybeOpenAsBrowser(intent, dataText);
            }
        } else {
            coordinateView.setText(coordinateText);
            saveLastCoordinate(coordinateText);
            saveCoordinateHistory(coordinateText);
            copyTextSilently(coordinateText);
            Toast.makeText(this, R.string.coordinate_copied, Toast.LENGTH_SHORT).show();
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 700);
            }
        }

        rawView.setText(rawText);
    }

    private void openDefaultBrowserSettings() {
        Intent intent = new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
        if (!tryStart(intent)) {
            tryStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
        }
    }

    private void openMapsSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS);
        intent.setData(Uri.parse("package:" + MAPS_PACKAGE));
        if (!tryStart(intent)) {
            Intent appDetails = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetails.setData(Uri.parse("package:" + MAPS_PACKAGE));
            if (!tryStart(appDetails)) {
                tryStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        }
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isCoordinateCopyEnabled() {
        return getPreferences().getBoolean(PREF_COPY_ENABLED, true);
    }

    private void saveCoordinateHistory(String coordinate) {
        String entry = HISTORY_TIME_FORMAT.format(new Date()) + "  " + coordinate;
        List<String> currentItems = getHistoryItems();
        StringBuilder builder = new StringBuilder(entry);
        int count = 1;
        for (String line : currentItems) {
            if (line.trim().isEmpty() || line.endsWith(coordinate) || count >= HISTORY_LIMIT) {
                continue;
            }
            builder.append("\n").append(line);
            count++;
        }
        getPreferences().edit().putString(PREF_HISTORY, builder.toString()).apply();
    }

    private void showCoordinateHistory() {
        List<String> items = getHistoryItems();
        Dialog dialog = bottomDialog();
        LinearLayout sheet = dialogSheet();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.HORIZONTAL);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(getString(R.string.coordinate_history), 22, COLOR_TEXT, true);
        titleGroup.addView(title, new LinearLayout.LayoutParams(-2, -2));

        TextView hintButton = infoButton(26);
        hintButton.setOnClickListener(v -> showInfoDialog(
                getString(R.string.coordinate_history),
                getString(R.string.history_hint)
        ));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        hintParams.setMargins(dp(8), 0, 0, 0);
        titleGroup.addView(hintButton, hintParams);
        titleRow.addView(titleGroup, new LinearLayout.LayoutParams(-2, -2));

        TextView clear = label(getString(R.string.clear_history), 14, COLOR_PRIMARY, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(10), dp(7), dp(10), dp(7));
        clear.setBackground(outlined(Color.TRANSPARENT, COLOR_PRIMARY, dp(16)));
        clear.setOnClickListener(v -> showConfirmDialog(
                getString(R.string.clear_history),
                getString(R.string.confirm_clear_history),
                getString(R.string.clear_history),
                () -> {
                    clearHistory();
                    dialog.dismiss();
                    showCoordinateHistory();
                }
        ));
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1);
        titleRow.addView(new View(this), spacerParams);
        titleRow.addView(clear, new LinearLayout.LayoutParams(-2, -2));
        sheet.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        if (items.isEmpty()) {
            TextView empty = label(getString(R.string.no_history), 16, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(24));
            sheet.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (String item : items) {
                View row = historyRow(item, dialog);
                addWithMargins(sheet, row, 0, dp(10), 0, 0);
            }
        }

        showBottomDialog(dialog, sheet);
    }

    private void copyHistoryItem(String item) {
        Matcher matcher = COORDINATE_PATTERN.matcher(item);
        if (matcher.find()) {
            String coordinate = truncateCoordinate(matcher.group(1), matcher.group(2));
            coordinateText = coordinate;
            coordinateView.setText(coordinate);
            saveLastCoordinate(coordinate);
            copyTextSilently(coordinate);
            Toast.makeText(this, R.string.coordinate_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLastCoordinate(String coordinate) {
        if (coordinate != null && !coordinate.isEmpty()) {
            getPreferences().edit().putString(PREF_LAST_COORDINATE, coordinate).apply();
        }
    }

    private View historyRow(String item, Dialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(rounded(COLOR_HISTORY, dp(18)));

        String time = getHistoryTime(item);
        String coordinate = getHistoryCoordinate(item);
        boolean isPinned = item.equals(getPreferences().getString(PREF_PINNED_HISTORY, ""));

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.CENTER_VERTICAL);
        attachHistoryPress(textArea, item, dialog);

        TextView timeView = label(time, 13, COLOR_MUTED, false);
        if (isPinned) {
            timeView.setText(getString(R.string.pinned_history_prefix) + "  " + time);
            timeView.setTextColor(COLOR_PRIMARY);
        }
        textArea.addView(timeView, new LinearLayout.LayoutParams(-1, -2));

        TextView coordinateView = label(coordinate, 17, COLOR_TEXT, true);
        coordinateView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        coordinateView.setPadding(0, dp(4), 0, 0);
        textArea.addView(coordinateView, new LinearLayout.LayoutParams(-1, -2));
        row.addView(textArea, new LinearLayout.LayoutParams(0, -2, 1));

        ImageButton share = iconButton(R.drawable.ic_share_simple, COLOR_TEXT);
        share.setContentDescription(getString(R.string.share_coordinate));
        share.setOnClickListener(v -> shareHistoryItem(item));
        row.addView(share, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageButton delete = iconButton(R.drawable.ic_delete_simple, COLOR_DANGER);
        delete.setContentDescription(getString(R.string.delete_history_item));
        delete.setOnClickListener(v -> showConfirmDialog(
                getString(R.string.delete_history_item),
                getString(R.string.confirm_delete_history),
                getString(R.string.delete_history_item),
                () -> {
                    deleteHistoryItem(item);
                    dialog.dismiss();
                    showCoordinateHistory();
                }
        ));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        deleteParams.setMargins(dp(6), 0, 0, 0);
        row.addView(delete, deleteParams);
        return row;
    }

    private void attachHistoryPress(View view, String item, Dialog dialog) {
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] longHandled = new boolean[1];
        Runnable longPress = () -> {
            longHandled[0] = true;
            togglePinHistoryItem(item);
            dialog.dismiss();
            showCoordinateHistory();
        };
        view.setOnTouchListener((target, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    longHandled[0] = false;
                    handler.postDelayed(longPress, 500);
                    target.setAlpha(0.72f);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    target.setAlpha(1f);
                    if (!longHandled[0]) {
                        copyHistoryItem(item);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    target.setAlpha(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private ImageButton iconButton(int iconRes, int tint) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setColorFilter(tint);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        return button;
    }

    private List<String> getHistoryItems() {
        String history = getPreferences().getString(PREF_HISTORY, "");
        String pinned = getPreferences().getString(PREF_PINNED_HISTORY, "");
        ArrayList<String> items = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            String[] lines = history.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !items.contains(trimmed)) {
                    items.add(trimmed);
                }
            }
        }
        if (pinned != null && !pinned.isEmpty() && items.remove(pinned)) {
            items.add(0, pinned);
        }
        return items;
    }

    private void writeHistoryItems(List<String> items) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String item : items) {
            if (item.trim().isEmpty() || count >= HISTORY_LIMIT) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(item);
            count++;
        }
        getPreferences().edit().putString(PREF_HISTORY, builder.toString()).apply();
    }

    private void clearHistory() {
        getPreferences().edit()
                .remove(PREF_HISTORY)
                .remove(PREF_PINNED_HISTORY)
                .apply();
        Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
    }

    private void deleteHistoryItem(String item) {
        List<String> items = getHistoryItems();
        items.remove(item);
        SharedPreferences.Editor editor = getPreferences().edit();
        if (item.equals(getPreferences().getString(PREF_PINNED_HISTORY, ""))) {
            editor.remove(PREF_PINNED_HISTORY);
        }
        editor.apply();
        writeHistoryItems(items);
        Toast.makeText(this, R.string.history_deleted, Toast.LENGTH_SHORT).show();
    }

    private void togglePinHistoryItem(String item) {
        String pinned = getPreferences().getString(PREF_PINNED_HISTORY, "");
        if (item.equals(pinned)) {
            getPreferences().edit().remove(PREF_PINNED_HISTORY).apply();
            Toast.makeText(this, R.string.history_unpinned, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> items = getHistoryItems();
        if (items.remove(item)) {
            items.add(0, item);
            writeHistoryItems(items);
        }
        getPreferences().edit().putString(PREF_PINNED_HISTORY, item).apply();
        Toast.makeText(this, R.string.history_pinned, Toast.LENGTH_SHORT).show();
    }

    private void shareHistoryItem(String item) {
        String coordinate = getHistoryCoordinate(item);
        if (coordinate.isEmpty()) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, coordinate);
        tryStart(Intent.createChooser(share, getString(R.string.share_coordinate)));
    }

    private String getHistoryTime(String item) {
        return item.length() >= 14 ? item.substring(0, 14).trim() : "";
    }

    private String getHistoryCoordinate(String item) {
        Matcher matcher = COORDINATE_PATTERN.matcher(item);
        if (matcher.find()) {
            return truncateCoordinate(matcher.group(1), matcher.group(2));
        }
        return item.length() > 16 ? item.substring(16).trim() : item;
    }

    private void showInfoDialog(String title, String message) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = dialogSheet();
        sheet.addView(label(title, 22, COLOR_TEXT, true));

        TextView body = label(message, 16, COLOR_MUTED, false);
        body.setLineSpacing(dp(3), 1.0f);
        addWithMargins(sheet, body, 0, dp(14), 0, dp(18));

        Button ok = button(getString(R.string.close_dialog), true);
        ok.setOnClickListener(v -> dialog.dismiss());
        sheet.addView(ok, new LinearLayout.LayoutParams(-1, dp(50)));

        showBottomDialog(dialog, sheet);
    }

    private void showConfirmDialog(String title, String message, String confirmText, Runnable onConfirm) {
        Dialog dialog = bottomDialog();
        LinearLayout sheet = dialogSheet();
        sheet.addView(label(title, 22, COLOR_TEXT, true));

        TextView body = label(message, 16, COLOR_MUTED, false);
        body.setLineSpacing(dp(3), 1.0f);
        addWithMargins(sheet, body, 0, dp(14), 0, dp(18));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button cancel = button(getString(R.string.cancel_dialog), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button confirm = button(confirmText, true);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        confirmParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(confirm, confirmParams);

        sheet.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        showBottomDialog(dialog, sheet);
    }

    private void showRawDataDialog() {
        if (rawText == null || rawText.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }

        copyTextSilently(rawText);
        Toast.makeText(this, R.string.raw_data_copied, Toast.LENGTH_SHORT).show();

        Dialog dialog = bottomDialog();
        LinearLayout sheet = dialogSheet();
        sheet.addView(label(getString(R.string.raw_data), 22, COLOR_TEXT, true));

        TextView body = label(rawText, 12, COLOR_MUTED, false);
        body.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        body.setTextIsSelectable(true);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        body.setBackground(rounded(Color.rgb(248, 246, 241), dp(14)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        addWithMargins(sheet, scroll, 0, dp(14), 0, dp(18));
        scroll.getLayoutParams().height = dp(260);

        Button close = button(getString(R.string.close_dialog), true);
        close.setOnClickListener(v -> dialog.dismiss());
        sheet.addView(close, new LinearLayout.LayoutParams(-1, dp(50)));

        showBottomDialog(dialog, sheet);
    }

    private Dialog bottomDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private void showBottomDialog(Dialog dialog, LinearLayout sheet) {
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private LinearLayout dialogSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(20), dp(20), dp(22));
        sheet.setBackground(rounded(COLOR_CARD, dp(28)));
        return sheet;
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private boolean openInChrome(Intent sourceIntent, Uri data) {
        if (!Intent.ACTION_VIEW.equals(sourceIntent.getAction()) || data == null) {
            return false;
        }
        String scheme = data.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        if (tryOpenChromeExplicit(data) || tryOpenExternalBrowser(data, CHROME_PACKAGE)) {
            return true;
        }
        return tryOpenAnyExternalBrowser(data);
    }

    private boolean isExternalViewLink(Intent intent, Uri data) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || data == null) {
            return false;
        }
        String scheme = data.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isMapsShortLink(Uri data) {
        return data != null && "maps.app.goo.gl".equalsIgnoreCase(data.getHost());
    }

    private boolean isWithinShortLinkGuard() {
        long lastShortLinkTime = getPreferences().getLong(PREF_LAST_SHORT_LINK_TIME, 0);
        return lastShortLinkTime > 0
                && System.currentTimeMillis() - lastShortLinkTime <= SHORT_LINK_GUARD_MS;
    }

    private boolean shouldIgnoreCoordinateLink(Uri data, String decodedData) {
        String host = data.getHost();
        if ("maps.app.goo.gl".equalsIgnoreCase(host)) {
            return true;
        }
        return !isGoogleMapsLink(decodedData);
    }

    private boolean isExcludedLinkSource(Intent intent) {
        Uri referrer = getReferrer();
        if (isExcludedSourceUri(referrer)) {
            return true;
        }

        Uri extraReferrer = intent.getParcelableExtra(Intent.EXTRA_REFERRER);
        if (isExcludedSourceUri(extraReferrer)) {
            return true;
        }

        String referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
        return isExcludedSourceText(referrerName);
    }

    private boolean isExcludedSourceUri(Uri source) {
        if (source == null) {
            return false;
        }
        if ("android-app".equalsIgnoreCase(source.getScheme())) {
            return isExcludedSourcePackage(source.getHost());
        }
        return isExcludedSourceText(source.toString());
    }

    private boolean isExcludedSourceText(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        String lower = source.toLowerCase(Locale.US);
        return lower.contains(CHROME_PACKAGE)
                || lower.contains(GOOGLE_PLAY_SERVICES_PACKAGE)
                || lower.contains(GOOGLE_APP_PACKAGE)
                || lower.contains(MAPS_PACKAGE)
                || lower.contains("com.mi.globalbrowser")
                || lower.contains("com.sec.android.app.sbrowser")
                || lower.contains("com.betafish.adblocksbrowser")
                || lower.contains("com.microsoft.emmx")
                || lower.contains("org.mozilla.firefox");
    }

    private boolean isExcludedSourcePackage(String packageName) {
        return isExcludedSourceText(packageName);
    }

    private boolean isGoogleMapsLink(String decodedData) {
        String lower = decodedData.toLowerCase(Locale.US);
        return lower.contains("google.com/maps")
                || lower.contains("maps.google.com")
                || lower.contains("www.google.com/maps");
    }

    private boolean tryOpenChromeExplicit(Uri data) {
        Intent chromeIntent = browserIntent(data);
        chromeIntent.setComponent(new ComponentName(CHROME_PACKAGE, CHROME_TABBED_ACTIVITY));
        if (startExternalBrowser(chromeIntent)) {
            return true;
        }

        chromeIntent = browserIntent(data);
        chromeIntent.setComponent(new ComponentName(CHROME_PACKAGE, CHROME_DISPATCHER));
        return startExternalBrowser(chromeIntent);
    }

    private boolean tryOpenExternalBrowser(Uri data, String packageName) {
        Intent browserIntent = browserIntent(data);
        browserIntent.setPackage(packageName);
        return startExternalBrowser(browserIntent);
    }

    private boolean tryOpenAnyExternalBrowser(Uri data) {
        Intent browserIntent = browserIntent(data);
        List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(browserIntent, 0);
        String ownPackage = getPackageName();
        for (ResolveInfo candidate : candidates) {
            if (candidate.activityInfo == null) {
                continue;
            }
            String packageName = candidate.activityInfo.packageName;
            if (ownPackage.equals(packageName) || MAPS_PACKAGE.equals(packageName)) {
                continue;
            }
            Intent explicitIntent = browserIntent(data);
            explicitIntent.setComponent(new ComponentName(packageName, candidate.activityInfo.name));
            if (startExternalBrowser(explicitIntent)) {
                return true;
            }
        }
        return false;
    }

    private Intent browserIntent(Uri data) {
        Intent intent = new Intent(Intent.ACTION_VIEW, data);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean startExternalBrowser(Intent intent) {
        try {
            startActivity(intent);
            finish();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void maybeOpenAsBrowser(Intent intent, String dataText) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || dataText.isEmpty()) {
            return;
        }
        if (dataText.startsWith("http://") || dataText.startsWith("https://")) {
            rawView.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(dataText);
        }
    }

    private String buildRawText(Intent intent, String dataText, String sharedText, String decodedData) {
        StringBuilder builder = new StringBuilder();
        builder.append("Action: ").append(intent.getAction()).append("\n\n");
        builder.append("Type: ").append(intent.getType()).append("\n\n");
        builder.append("Referrer:\n").append(getReferrer()).append("\n\n");
        builder.append("Extra referrer:\n").append(String.valueOf(intent.getParcelableExtra(Intent.EXTRA_REFERRER))).append("\n\n");
        builder.append("Extra referrer name:\n").append(intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)).append("\n\n");
        builder.append("Data:\n").append(dataText).append("\n\n");
        builder.append("Shared text:\n").append(sharedText == null ? "" : sharedText).append("\n\n");
        builder.append("Decoded data:\n").append(decodedData).append("\n\n");
        builder.append("Intent URI:\n").append(intent.toUri(Intent.URI_INTENT_SCHEME));
        return builder.toString();
    }

    private String findCoordinate(String text) {
        Matcher matcher = COORDINATE_PATTERN.matcher(text);
        while (matcher.find()) {
            double lat = Double.parseDouble(matcher.group(1));
            double lng = Double.parseDouble(matcher.group(2));
            if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                return truncateCoordinate(matcher.group(1), matcher.group(2));
            }
        }
        return "";
    }

    private String truncateCoordinate(String lat, String lng) {
        return truncateDecimal(lat, 6) + "," + truncateDecimal(lng, 6);
    }

    private String truncateDecimal(String value, int places) {
        int dot = value.indexOf('.');
        if (dot < 0) {
            return value;
        }
        int end = Math.min(value.length(), dot + places + 1);
        return value.substring(0, end);
    }

    private String getLastCoordinateOrPlaceholder() {
        String lastCoordinate = getPreferences().getString(PREF_LAST_COORDINATE, "");
        return lastCoordinate == null || lastCoordinate.isEmpty()
                ? getString(R.string.no_coordinate)
                : lastCoordinate;
    }

    private String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return text;
        }
    }

    private void copyText(String label, String value) {
        if (value == null || value.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        copyTextSilently(value);
        Toast.makeText(this, "Copied " + label, Toast.LENGTH_SHORT).show();
    }

    private void copyTextSilently(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("Pikmin coordinate", value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }
}
