package com.headunit.flatlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FlatActivity extends Activity {

    private static final String TAG = "FlatLauncher";
    private static final String PREFS = "flat";
    private static final String KEY_LAST_APP = "last_app";
    private static final long STATS_INTERVAL_MS = 2000L;

    private AppGridAdapter appAdapter;
    private BroadcastReceiver packageReceiver;
    private final SystemStats systemStats = new SystemStats();
    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView ampmPill;
    private BroadcastReceiver btPlayReceiver;

    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            updateStatsLine();
            statsHandler.postDelayed(this, STATS_INTERVAL_MS);
        }
    };
    private void setupBtAutoSource() {
        btPlayReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                Log.d(TAG, "bt event: " + action + " state=" + state);
                boolean connect = "android.bluetooth.device.action.ACL_CONNECTED".equals(action)
                        || state == 2;
                if (!connect) {
                    return;
                }
                Intent i = new Intent();
                i.setClassName("com.autochips.bluetooth", "com.autochips.bluetooth.BtMusicActivity");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (RuntimeException e) {
                    Log.d(TAG, "cannot start BT music source: " + e);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        f.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        f.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(btPlayReceiver, f);
    }
    static class PinnedApp {
        String label;
        Intent intent;
        int iconRes;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_flat);
        buildPinnedRow(resolvePinned());
        setupAppGrid();
        ampmPill = (TextView) findViewById(R.id.ampm_pill);
        setupBtAutoSource();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private List<PinnedApp> resolvePinned() {
        PackageManager pm = getPackageManager();
        List<PinnedApp> out = new ArrayList<PinnedApp>();
        pin(out, getString(R.string.pin_radio), pm.getLaunchIntentForPackage("com.hcn.autoradio"), R.drawable.ic_radio);
        pin(out, getString(R.string.pin_media), pm.getLaunchIntentForPackage("com.hcn.AutoMediaPlayer"), R.drawable.ic_music);
        // ACTION_BLUETOOTH_SETTINGS has no handler on this firmware;
        // the unit's Bluetooth UI is com.autochips.bluetooth.MainBluetoothActivity.
        pin(out, getString(R.string.pin_bluetooth), pm.getLaunchIntentForPackage("com.autochips.bluetooth"), R.drawable.ic_bluetooth);
        pin(out, getString(R.string.pin_maps), pm.getLaunchIntentForPackage("com.google.android.apps.maps"), R.drawable.ic_maps);
        return out;
    }

    private void pin(List<PinnedApp> out, String label, Intent intent, int iconRes) {
        if (intent == null) {
            Log.d(TAG, "dropped pinned button, no handler: " + label);
            return;
        }
        PinnedApp app = new PinnedApp();
        app.label = label;
        app.intent = intent;
        app.iconRes = iconRes;
        out.add(app);
    }

    private void buildPinnedRow(List<PinnedApp> apps) {
        LinearLayout row = (LinearLayout) findViewById(R.id.pinned_row);
        int minHeight = getResources().getDimensionPixelSize(R.dimen.pinned_min_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.pinned_gap);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.grid_icon);
        float textSize = getResources().getDimension(R.dimen.pinned_text_size);
        int muted = getResources().getColor(R.color.muted);
        for (int i = 0; i < apps.size(); i++) {
            final PinnedApp app = apps.get(i);
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setMinimumHeight(minHeight);
            b.setBackgroundResource(R.drawable.pinned_button_bg);
            b.setPadding(iconSize, 0, iconSize, 0);
            ImageView icon = new ImageView(this);
            icon.setImageResource(app.iconRes);
            icon.setColorFilter(muted);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
            b.addView(icon, ip);
            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextSize(pxToSp(textSize));
            label.setTextColor(getResources().getColor(R.color.text));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = iconSize;
            b.addView(label, lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(app.intent);
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rp.topMargin = gap;
            }
            row.addView(b, rp);
        }
    }

    private void setupAppGrid() {
        GridView grid = (GridView) findViewById(R.id.app_grid);
        TextView empty = (TextView) findViewById(R.id.empty_grid);
        appAdapter = new AppGridAdapter(this);
        appAdapter.setHighlight(prefs.getString(KEY_LAST_APP, null));
        grid.setAdapter(appAdapter);
        updateEmptyState();
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppGridAdapter.App app = appAdapter.getItem(position);
                prefs.edit().putString(KEY_LAST_APP, app.pkg).apply();
                appAdapter.setHighlight(app.pkg);
                startActivity(app.launch);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final AppGridAdapter.App app = appAdapter.getItem(position);
                new AlertDialog.Builder(FlatActivity.this)
                        .setTitle(app.label)
                        .setItems(new CharSequence[]{getString(R.string.app_info)},
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", app.pkg, null));
                                        startActivity(i);
                                    }
                                })
                        .show();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        statsHandler.removeCallbacks(statsTick);
        updateStatsLine();
        statsHandler.postDelayed(statsTick, STATS_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statsHandler.removeCallbacks(statsTick);
    }

    private void updateStatsLine() {
        TextView ram = (TextView) findViewById(R.id.ram_val);
        TextView cpu = (TextView) findViewById(R.id.cpu_val);
        SystemStats.Snapshot s = systemStats.sample(
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE), System.currentTimeMillis());
        if (ram != null) {
            ram.setText(getString(R.string.ram_fmt, fmtGb(s.ramUsedBytes), fmtGb(s.ramTotalBytes)));
        }
        if (cpu != null) {
            cpu.setText(s.cpuPercent >= 0 ? getString(R.string.cpu_fmt, s.cpuPercent) : "CPU —");
        }
        if (ampmPill != null) {
            if (DateFormat.is24HourFormat(this)) {
                ampmPill.setVisibility(View.GONE);
            } else {
                ampmPill.setVisibility(View.VISIBLE);
                ampmPill.setText(Calendar.getInstance().get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
            }
        }
    }

    private static String fmtGb(long bytes) {
        return String.format("%.1f", bytes / 1073741824f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Catch package changes that happened while stopped (receiver is unregistered then).
        if (appAdapter != null) {
            appAdapter.requery();
            updateEmptyState();
        }
        if (packageReceiver == null) {
            packageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (appAdapter == null || intent == null || intent.getData() == null) {
                        return;
                    }
                    String pkg = intent.getData().getSchemeSpecificPart();
                    if (getPackageName().equals(pkg)) {
                        return;
                    }
                    Log.d(TAG, "package changed: " + intent.getAction() + " " + pkg);
                    appAdapter.requery();
                    updateEmptyState();
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addDataScheme("package");
            registerReceiver(packageReceiver, f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }
    }

    private void updateEmptyState() {
        GridView grid = (GridView) findViewById(R.id.app_grid);
        TextView empty = (TextView) findViewById(R.id.empty_grid);
        TextView count = (TextView) findViewById(R.id.apps_count);
        boolean isEmpty = appAdapter.getCount() == 0;
        grid.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        count.setText(String.valueOf(appAdapter.getCount()));
    }

    private float pxToSp(float px) {
        return px / getResources().getDisplayMetrics().scaledDensity;
    }
}
