package com.kizakir.pikspot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InterceptorActivity extends Activity {
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
    private static final Pattern COORDINATE_PATTERN = Pattern.compile(
            "(-?\\d{1,2}\\.\\d{4,})\\s*,\\s*(-?\\d{1,3}\\.\\d{4,})"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finishSilently();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        finishSilently();
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
            openInChrome(intent, data);
            return;
        }

        if (!isCoordinateCopyEnabled() && openInChrome(intent, data)) {
            return;
        }

        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        String combinedData = dataText + "\n" + (sharedText == null ? "" : sharedText);
        String decodedData = decode(combinedData);
        String coordinate = findCoordinate(decodedData);

        if (!coordinate.isEmpty() && isExternalViewLink(intent, data)
                && (isWithinShortLinkGuard() || isExcludedLinkSource(intent))) {
            openInChrome(intent, data);
            return;
        }

        if (!coordinate.isEmpty() && isExternalViewLink(intent, data)
                && shouldIgnoreCoordinateLink(data, decodedData)) {
            openInChrome(intent, data);
            return;
        }

        if (coordinate.isEmpty()) {
            openInChrome(intent, data);
            return;
        }

        saveLastCoordinate(coordinate);
        saveCoordinateHistory(coordinate);
        copyTextSilently(coordinate);
        Toast.makeText(this, R.string.coordinate_copied, Toast.LENGTH_SHORT).show();
    }

    private boolean isCoordinateCopyEnabled() {
        return getPreferences().getBoolean(PREF_COPY_ENABLED, true);
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void saveLastCoordinate(String coordinate) {
        if (coordinate != null && !coordinate.isEmpty()) {
            getPreferences().edit().putString(PREF_LAST_COORDINATE, coordinate).apply();
        }
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
            return true;
        } catch (Exception ignored) {
            return false;
        }
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

    private String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return text;
        }
    }

    private void copyTextSilently(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("PikSpot coordinate", value));
    }

    private void finishSilently() {
        finish();
        overridePendingTransition(0, 0);
    }
}
