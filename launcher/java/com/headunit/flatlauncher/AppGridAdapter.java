package com.headunit.flatlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppGridAdapter extends BaseAdapter {

    static class App {
        CharSequence label;
        Drawable icon;
        Intent launch;
        String pkg;
        String cls;
        boolean restart;
    }

    private final Context context;
    private final List<App> apps = new ArrayList<App>();
    private final ColorMatrixColorFilter desaturate;
    private String highlightKey;

    AppGridAdapter(Context ctx) {
        context = ctx;
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0.25f);
        desaturate = new ColorMatrixColorFilter(m);
        query();
    }

    void setHighlight(String key) {
        highlightKey = key;
        notifyDataSetChanged();
    }

    private void query() {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            String key = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            App app = new App();
            app.label = ri.loadLabel(pm);
            app.icon = ri.loadIcon(pm);
            app.pkg = ri.activityInfo.packageName;
            app.cls = ri.activityInfo.name;
            app.launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.pkg, app.cls);
            apps.add(app);
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App l, App r) {
                return String.valueOf(l.label).compareToIgnoreCase(String.valueOf(r.label));
            }
        });
        addRestartTile();
    }

    private void addRestartTile() {
        App app = new App();
        app.label = context.getString(R.string.restart_label);
        app.icon = null;
        app.pkg = "flat.restart";
        app.cls = "restart";
        app.restart = true;
        apps.add(app);
    }

    void requery() {
        apps.clear();
        query();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public App getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(context).inflate(R.layout.grid_item, parent, false);
        }
        App app = getItem(position);
        boolean hi = app.pkg != null && (app.pkg + "/" + app.cls).equals(highlightKey);
        v.setBackgroundResource(hi ? R.drawable.tile_card_bg_hi : R.drawable.tile_card_bg);
        FrameLayout plate = (FrameLayout) v.findViewById(R.id.plate);
        plate.setBackgroundResource(hi ? R.drawable.plate_bg_hi : R.drawable.plate_bg);
        ImageView icon = (ImageView) v.findViewById(R.id.icon);
        TextView label = (TextView) v.findViewById(R.id.label);
        int tint = context.getResources().getColor(hi ? R.color.accent : R.color.muted);
        Integer glyph = glyphFor(app);
        if (glyph != null) {
            icon.setImageResource(glyph);
            icon.setColorFilter(tint);
        } else {
            icon.setImageResource(R.drawable.ic_g_app);
            icon.setColorFilter(tint);
        }
        label.setText(app.label);
        label.setTextColor(context.getResources().getColor(hi ? R.color.accent : R.color.text));
        return v;

    }
    private Integer glyphFor(App app) {
        if (app.restart) {
            return R.drawable.ic_g_restart;
        }
        String c = app.cls == null ? "" : app.cls;
        if ("com.google.android.apps.maps".equals(app.pkg)) return R.drawable.ic_maps;
        if ("com.google.android.youtube".equals(app.pkg)) return R.drawable.ic_g_play;
        if ("com.android.chrome".equals(app.pkg)) return R.drawable.ic_g_globe;
        if ("com.android.vending".equals(app.pkg)) return R.drawable.ic_g_bag;
        if ("com.android.settings".equals(app.pkg)) return R.drawable.ic_g_settings;
        if ("com.mediatek.filemanager".equals(app.pkg)) return R.drawable.ic_g_folder;
        if ("com.hcn.AutoMediaPlayer".equals(app.pkg)) return c.contains("Video") ? R.drawable.ic_g_video : R.drawable.ic_g_audio;
        if ("com.hcn.autoradio".equals(app.pkg)) return R.drawable.ic_g_radio;
        if ("com.autochips.bluetooth".equals(app.pkg)) return c.contains("BtMusic") ? R.drawable.ic_g_audio : R.drawable.ic_g_bluetooth;
        if ("com.ankai.cardvr".equals(app.pkg)) return R.drawable.ic_g_camera;
        if ("com.auto.hcamera".equals(app.pkg)) return R.drawable.ic_g_monitor;
        if ("com.hcn.autoeq".equals(app.pkg)) return R.drawable.ic_g_sliders;
        if ("com.hcn.themepicker".equals(app.pkg)) return R.drawable.ic_g_palette;
        if ("com.hcn.autosetting".equals(app.pkg)) return R.drawable.ic_g_car;
        if ("com.hcn.autocan".equals(app.pkg)) return R.drawable.ic_g_gauge;
        if ("com.hcn.autofan".equals(app.pkg)) return R.drawable.ic_g_fan;
        if ("com.hcn.gallery".equals(app.pkg)) return R.drawable.ic_g_image;
        if ("com.hcn.mcuupgrade".equals(app.pkg)) return R.drawable.ic_g_cpu;
        if ("com.android.launcher3".equals(app.pkg)) return R.drawable.ic_g_grid;
        if ("com.hcn.swc".equals(app.pkg)) return R.drawable.ic_g_swc;
        if ("com.hcn.screensaver".equals(app.pkg)) return R.drawable.ic_g_image;
        if ("com.shihab.diplay".equals(app.pkg)) return R.drawable.ic_g_phone;
        if ("com.andrerinas.headunitrevived".equals(app.pkg)) return R.drawable.ic_g_carfront;
        return null;
    }
}
