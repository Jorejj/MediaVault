package com.example.mediavault.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mediavault.AppExecutor;
import com.example.mediavault.R;
import com.example.mediavault.service.MediaMonitorService;
import com.example.mediavault.utils.DefaultAppWhitelist;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MonitoredAppsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private AppAdapter adapter;
    private final List<AppItem> allLaunchableApps = new ArrayList<>();
    private static final String PREFS_NAME = "monitored_apps_prefs";
    private static final String KEY_PACKAGE_SET = "monitored_packages";
    private static final String KEY_FIRST_RUN = "first_run_monitored_apps";
    private static final String KEY_OPEN_ADD_DIALOG_ONCE = "open_add_dialog_once";
    private static final String KEY_MIHON_DEFAULT_MIGRATED = "mihon_default_migrated";
    private static final String KEY_BILIBILI_DEFAULT_MIGRATED = "bilibili_default_migrated";
    private static final String KEY_YOUTUBE_DEFAULT_MIGRATED = "youtube_default_migrated";
    private static final String KEY_CORE_DEFAULTS_MIGRATED = "core_defaults_migrated";

    private static final Set<String> DEFAULT_MONITORED_PACKAGES = DefaultAppWhitelist.get();
    private static final Set<String> EXCLUDED_NON_MEDIA_PACKAGES = new HashSet<>();
    static {
        EXCLUDED_NON_MEDIA_PACKAGES.add("com.android.vending");
        EXCLUDED_NON_MEDIA_PACKAGES.add("com.google.android.apps.nexuslauncher");
        EXCLUDED_NON_MEDIA_PACKAGES.add("com.google.android.googlequicksearchbox");
        EXCLUDED_NON_MEDIA_PACKAGES.add("com.android.settings");
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitored_apps, container, false);
        recyclerView = view.findViewById(R.id.recycler_monitored_apps);
        progressBar = view.findViewById(R.id.progress_monitored_apps);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        loadApps();

        view.findViewById(R.id.btn_reset_monitoring).setOnClickListener(v -> resetToDefaults());
        View addButton = view.findViewById(R.id.btn_add_monitored_app);
        addButton.setOnClickListener(v -> showAddAppDialog());
        addButton.setOnLongClickListener(v -> {
            showAddAppDialog();
            return true;
        });
        View allAppsButton = view.findViewById(R.id.btn_open_android_app_list);
        allAppsButton.setOnClickListener(v -> showAllAppsDialog());
        allAppsButton.setOnLongClickListener(v -> {
            showAllAppsDialog();
            return true;
        });
        
        return view;
    }

    private void resetToDefaults() {
        if (adapter == null) return;
        
        for (AppItem item : adapter.items) {
            item.isSelected = DEFAULT_MONITORED_PACKAGES.contains(item.packageName);
        }
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        saveMonitoredApps();
        ToastUtils.showCustomToast(getContext(), "Monitoring reset to recommended apps");
    }

    private void loadApps() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        progressBar.setVisibility(View.VISIBLE);
        AppExecutor.getInstance().diskIO().execute(() -> {
            PackageManager pm = appContext.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppItem> appItems = new ArrayList<>();
            List<AppItem> launchableApps = new ArrayList<>();
            
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true);
            boolean migrateMihonDefault = !prefs.getBoolean(KEY_MIHON_DEFAULT_MIGRATED, false);
            boolean migrateBilibiliDefault = !prefs.getBoolean(KEY_BILIBILI_DEFAULT_MIGRATED, false);
            boolean migrateYoutubeDefault = !prefs.getBoolean(KEY_YOUTUBE_DEFAULT_MIGRATED, false);
            boolean migrateCoreDefaults = !prefs.getBoolean(KEY_CORE_DEFAULTS_MIGRATED, false);
            Set<String> selectedPackages = new HashSet<>(prefs.getStringSet(KEY_PACKAGE_SET, new HashSet<>()));
            if (migrateCoreDefaults) {
                selectedPackages.addAll(DefaultAppWhitelist.getCoreRecommendedPackages());
            }
            Set<String> invalidPackages = new HashSet<>();
            for (String selectedPkg : selectedPackages) {
                if (shouldExcludePackage(selectedPkg)) {
                    invalidPackages.add(selectedPkg);
                }
            }
            selectedPackages.removeAll(invalidPackages);

            for (ApplicationInfo app : apps) {
                if (shouldExcludePackage(app.packageName)) {
                    continue;
                }
                String appName = app.loadLabel(pm).toString();
                boolean isSelected;
                if (isFirstRun) {
                    // On first run, auto-select if it's in our essential list
                    isSelected = DEFAULT_MONITORED_PACKAGES.contains(app.packageName) || isMihonPackage(app.packageName);
                    if (isSelected) selectedPackages.add(app.packageName);
                } else {
                    isSelected = selectedPackages.contains(app.packageName);
                    if (!isSelected && migrateMihonDefault && isMihonPackage(app.packageName)) {
                        isSelected = true;
                        selectedPackages.add(app.packageName);
                    } else if (!isSelected && migrateBilibiliDefault && isBilibiliPackage(app.packageName)) {
                        isSelected = true;
                        selectedPackages.add(app.packageName);
                    } else if (!isSelected && migrateYoutubeDefault && isYoutubePackage(app.packageName)) {
                        isSelected = true;
                        selectedPackages.add(app.packageName);
                    }
                }

                Drawable icon = app.loadIcon(pm);
                AppItem launchable = new AppItem(appName, app.packageName, icon, isSelected);
                launchableApps.add(launchable);

                if (isSelected || DEFAULT_MONITORED_PACKAGES.contains(app.packageName) || isLikelyMediaApp(appName, app.packageName)) {
                    appItems.add(new AppItem(appName, app.packageName, icon, isSelected));
                }
            }

            Map<String, AppItem> byPackage = new HashMap<>();
            for (AppItem app : launchableApps) {
                byPackage.put(app.packageName, app);
            }
            Set<String> listedPackages = new HashSet<>();
            for (AppItem app : appItems) {
                listedPackages.add(app.packageName);
            }
            for (String selectedPackage : selectedPackages) {
                if (!listedPackages.contains(selectedPackage)) {
                    AppItem selectedApp = byPackage.get(selectedPackage);
                    if (selectedApp != null) {
                        appItems.add(new AppItem(
                                selectedApp.name,
                                selectedApp.packageName,
                                selectedApp.icon,
                                true
                        ));
                    }
                }
            }
            
            // If first run, save the auto-selected defaults immediately
            if (isFirstRun || migrateMihonDefault || migrateBilibiliDefault || migrateYoutubeDefault || migrateCoreDefaults) {
                prefs.edit()
                    .putStringSet(KEY_PACKAGE_SET, selectedPackages)
                    .putBoolean(KEY_FIRST_RUN, false)
                    .putBoolean(KEY_MIHON_DEFAULT_MIGRATED, true)
                    .putBoolean(KEY_BILIBILI_DEFAULT_MIGRATED, true)
                    .putBoolean(KEY_YOUTUBE_DEFAULT_MIGRATED, true)
                    .putBoolean(KEY_CORE_DEFAULTS_MIGRATED, true)
                    .apply();
                
                // Broadcast the initial whitelist to the service
                Intent intent = new Intent(MediaMonitorService.ACTION_MONITORED_APPS_CHANGED);
                intent.setPackage(appContext.getPackageName());
                appContext.sendBroadcast(intent);
            }

            launchableApps.sort(Comparator.comparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
            
            appItems.sort(Comparator.comparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));

            AppExecutor.getInstance().mainThread().execute(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                allLaunchableApps.clear();
                allLaunchableApps.addAll(launchableApps);
                adapter = new AppAdapter(appItems);
                recyclerView.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);
                maybeOpenPendingAddDialog();
            });
        });
    }

    private void maybeOpenPendingAddDialog() {
        if (!isAdded()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_OPEN_ADD_DIALOG_ONCE, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_OPEN_ADD_DIALOG_ONCE, false).apply();
        recyclerView.post(this::showAddAppDialog);
    }

    private void saveMonitoredApps() {
        Context context = getContext();
        if (adapter == null || context == null) return;
        
        Set<String> selectedPackages = new HashSet<>();
        Set<String> visiblePackages = new HashSet<>();
        for (AppItem item : adapter.items) {
            visiblePackages.add(item.packageName);
            if (item.isSelected) {
                selectedPackages.add(item.packageName);
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> existing = new HashSet<>(prefs.getStringSet(KEY_PACKAGE_SET, new HashSet<>()));
        for (String pkg : existing) {
            if (!visiblePackages.contains(pkg)) {
                selectedPackages.add(pkg);
            }
        }
        prefs.edit().putStringSet(KEY_PACKAGE_SET, selectedPackages).apply();

        // Broadcast to Service
        Intent intent = new Intent(MediaMonitorService.ACTION_MONITORED_APPS_CHANGED);
        intent.setPackage(context.getPackageName()); // Secure the broadcast
        context.sendBroadcast(intent);
    }

    private void showAddAppDialog() {
        if (adapter == null || allLaunchableApps.isEmpty()) {
            ToastUtils.showCustomToast(getContext(), "App list is still loading. Please try again.");
            return;
        }

        Map<String, AppItem> existingByPackage = new HashMap<>();
        for (AppItem item : adapter.items) {
            existingByPackage.put(item.packageName, item);
        }

        List<AppItem> candidates = new ArrayList<>();
        for (AppItem app : allLaunchableApps) {
            if (!existingByPackage.containsKey(app.packageName)) {
                candidates.add(new AppItem(app.name, app.packageName, app.icon, false));
            }
        }

        if (candidates.isEmpty()) {
            ToastUtils.showCustomToast(getContext(), "No additional apps available");
            return;
        }

        CharSequence[] labels = new CharSequence[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = candidates.get(i).name + " (" + candidates.get(i).packageName + ")";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add monitored app")
                .setItems(labels, (dialog, which) -> {
                    AppItem selected = candidates.get(which);
                    selected.isSelected = true;
                    adapter.items.add(selected);
                    adapter.items.sort(Comparator.comparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
                    adapter.notifyDataSetChanged();
                    saveMonitoredApps();
                    ToastUtils.showCustomToast(getContext(), "Added and monitoring enabled: " + selected.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAllAppsDialog() {
        if (adapter == null || allLaunchableApps.isEmpty()) {
            ToastUtils.showCustomToast(getContext(), "App list is still loading. Please try again.");
            return;
        }
        Map<String, AppItem> existingByPackage = new HashMap<>();
        for (AppItem item : adapter.items) {
            existingByPackage.put(item.packageName, item);
        }

        CharSequence[] labels = new CharSequence[allLaunchableApps.size()];
        boolean[] checked = new boolean[allLaunchableApps.size()];
        Set<String> selectedPackages = new HashSet<>();
        for (AppItem item : adapter.items) {
            if (item.isSelected) {
                selectedPackages.add(item.packageName);
            }
        }
        for (int i = 0; i < allLaunchableApps.size(); i++) {
            AppItem app = allLaunchableApps.get(i);
            labels[i] = app.name + " (" + app.packageName + ")";
            checked[i] = selectedPackages.contains(app.packageName);
        }

        Set<String> pendingSelection = new HashSet<>(selectedPackages);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("All installed apps")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String packageName = allLaunchableApps.get(which).packageName;
                    if (isChecked) {
                        pendingSelection.add(packageName);
                    } else {
                        pendingSelection.remove(packageName);
                    }
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    for (AppItem app : allLaunchableApps) {
                        AppItem existing = existingByPackage.get(app.packageName);
                        boolean shouldMonitor = pendingSelection.contains(app.packageName);
                        if (existing != null) {
                            existing.isSelected = shouldMonitor;
                        } else if (shouldMonitor) {
                            adapter.items.add(new AppItem(app.name, app.packageName, app.icon, true));
                        }
                    }
                    adapter.items.sort(Comparator.comparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
                    adapter.notifyDataSetChanged();
                    saveMonitoredApps();
                    ToastUtils.showCustomToast(getContext(), "Monitored apps updated");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean shouldExcludePackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        if (EXCLUDED_NON_MEDIA_PACKAGES.contains(packageName)) {
            return true;
        }
        return packageName.contains("camera");
    }

    private boolean isLikelyMediaApp(String appName, String packageName) {
        String name = appName == null ? "" : appName.toLowerCase(Locale.ROOT);
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        String combined = name + " " + pkg;
        return combined.contains("manga")
                || combined.contains("anime")
                || combined.contains("reader")
                || combined.contains("novel")
                || combined.contains("webtoon")
                || combined.contains("comic")
                || combined.contains("book")
                || combined.contains("episode")
                || combined.contains("stream")
                || combined.contains("video")
                || combined.contains("youtube")
                || combined.contains("movie")
                || combined.contains("browser")
                || combined.contains("chrome")
                || combined.contains("firefox")
                || combined.contains("brave")
                || combined.contains("tachiyomi")
                || combined.contains("mihon")
                || combined.contains("kotatsu")
                || combined.contains("kindle")
                || combined.contains("wattpad")
                || combined.contains("bilibili")
                || combined.contains("bstar");
    }

    private boolean isMihonPackage(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        return pkg.equals("app.mihon")
                || pkg.equals("app.mihon.foss")
                || pkg.startsWith("app.mihon.")
                || pkg.contains(".mihon");
    }

    private boolean isBilibiliPackage(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        return pkg.contains("bstar")
                || pkg.contains("bilibili")
                || pkg.contains("danmaku.bili");
    }

    private boolean isYoutubePackage(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.trim().toLowerCase(Locale.ROOT);
        return pkg.contains("youtube")
                || pkg.equals("app.rvx.android.youtube")
                || pkg.equals("com.vanced.android.youtube");
    }

    private static class AppItem {
        String name;
        String packageName;
        Drawable icon;
        boolean isSelected;

        AppItem(String name, String packageName, Drawable icon, boolean isSelected) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
            this.isSelected = isSelected;
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        List<AppItem> items;

        AppAdapter(List<AppItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monitored_app, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = items.get(position);
            holder.icon.setImageDrawable(item.icon);
            holder.name.setText(item.name);
            holder.pkg.setText(item.packageName);
            
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(item.isSelected);
            holder.toggle.setOnCheckedChangeListener((btn, isChecked) -> {
                item.isSelected = isChecked;
                saveMonitoredApps();
            });

            View.OnClickListener toggleClick = v -> holder.toggle.performClick();
            View.OnLongClickListener toggleLongPress = v -> {
                holder.toggle.performClick();
                String action = item.isSelected ? "Monitoring enabled for " : "Monitoring disabled for ";
                ToastUtils.showCustomToast(getContext(), action + item.name);
                return true;
            };

            holder.itemView.setOnClickListener(toggleClick);
            holder.itemView.setOnLongClickListener(toggleLongPress);
            holder.icon.setOnClickListener(toggleClick);
            holder.name.setOnClickListener(toggleClick);
            holder.pkg.setOnClickListener(toggleClick);
            holder.icon.setOnLongClickListener(toggleLongPress);
            holder.name.setOnLongClickListener(toggleLongPress);
            holder.pkg.setOnLongClickListener(toggleLongPress);
            holder.toggle.setOnLongClickListener(toggleLongPress);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, pkg;
            SwitchMaterial toggle;

            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.img_app_icon);
                name = v.findViewById(R.id.text_app_name);
                pkg = v.findViewById(R.id.text_app_package);
                toggle = v.findViewById(R.id.switch_monitor);
            }
        }
    }
}
