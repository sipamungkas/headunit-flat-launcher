package com.headunit.flatlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class FlatActivity extends Activity {

    private static final String TAG = "FlatLauncher";

    private AppGridAdapter appAdapter;
    private BroadcastReceiver packageReceiver;

    static class PinnedApp {
        String label;
        Intent intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flat);
        buildPinnedRow(resolvePinned());
        setupAppGrid();
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
        pin(out, getString(R.string.pin_radio), pm.getLaunchIntentForPackage("com.hcn.autoradio"));
        pin(out, getString(R.string.pin_media), pm.getLaunchIntentForPackage("com.hcn.AutoMediaPlayer"));
        // Ruling (Task 2): ACTION_BLUETOOTH_SETTINGS has no handler on this firmware;
        // the unit's Bluetooth UI is com.autochips.bluetooth.MainBluetoothActivity.
        pin(out, getString(R.string.pin_bluetooth), pm.getLaunchIntentForPackage("com.autochips.bluetooth"));
        pin(out, getString(R.string.pin_maps), pm.getLaunchIntentForPackage("com.google.android.apps.maps"));
        return out;
    }

    private void pin(List<PinnedApp> out, String label, Intent intent) {
        if (intent == null) {
            Log.d(TAG, "dropped pinned button, no handler: " + label);
            return;
        }
        PinnedApp app = new PinnedApp();
        app.label = label;
        app.intent = intent;
        out.add(app);
    }

    private void buildPinnedRow(List<PinnedApp> apps) {
        LinearLayout row = (LinearLayout) findViewById(R.id.pinned_row);
        int minHeight = getResources().getDimensionPixelSize(R.dimen.pinned_min_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.pinned_gap);
        float textSize = getResources().getDimension(R.dimen.pinned_text_size);
        for (int i = 0; i < apps.size(); i++) {
            final PinnedApp app = apps.get(i);
            Button b = new Button(this, null, 0);
            b.setText(app.label);
            b.setAllCaps(false);
            b.setTextSize(pxToSp(textSize));
            b.setTextColor(getResources().getColor(R.color.text));
            b.setGravity(Gravity.CENTER);
            b.setMinimumHeight(minHeight);
            b.setBackgroundResource(R.drawable.pinned_button_bg);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(app.intent);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = gap;
            }
            row.addView(b, lp);
        }
    }

    private void setupAppGrid() {
        GridView grid = (GridView) findViewById(R.id.app_grid);
        TextView empty = (TextView) findViewById(R.id.empty_grid);
        appAdapter = new AppGridAdapter(this);
        grid.setAdapter(appAdapter);
        updateEmptyState();
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivity(appAdapter.getItem(position).launch);
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
                                                Uri.fromParts("package", app.launch.getComponent().getPackageName(), null));
                                        startActivity(i);
                                    }
                                })
                        .show();
                return true;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        boolean isEmpty = appAdapter.getCount() == 0;
        grid.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private float pxToSp(float px) {
        return px / getResources().getDisplayMetrics().scaledDensity;
    }
}
