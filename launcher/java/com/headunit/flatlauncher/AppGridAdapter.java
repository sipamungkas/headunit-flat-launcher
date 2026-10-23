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
    }

    private final Context context;
    private final List<App> apps = new ArrayList<App>();
    private final ColorMatrixColorFilter desaturate;

    AppGridAdapter(Context ctx) {
        context = ctx;
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0.25f);
        desaturate = new ColorMatrixColorFilter(m);
        query();
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
            app.launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (app.launch == null) {
                app.launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            }
            apps.add(app);
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App l, App r) {
                return String.valueOf(l.label).compareToIgnoreCase(String.valueOf(r.label));
            }
        });
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
        ImageView icon = (ImageView) v.findViewById(R.id.icon);
        TextView label = (TextView) v.findViewById(R.id.label);
        icon.setImageDrawable(app.icon);
        icon.setColorFilter(desaturate);
        label.setText(app.label);
        return v;
    }
}
