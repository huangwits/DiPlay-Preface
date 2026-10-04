package com.shihab.diplay.diagnostics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Firmware identification plus readable code needed to inspect the actual E01 Bluetooth API. */
final class VendorInterfaceCollector {
    static File collect(Context context, String diagnostic) throws IOException {
        PackageManager pm = context.getPackageManager();
        StringBuilder report = new StringBuilder(diagnostic).append("\nE01 interface bundle v0.3\n")
            .append("Firmware code and service declarations only. No contacts, pairings, logcat or app data.\n")
            .append("No Bluetooth switch, Binder transaction or vendor service was invoked.\n")
            .append("Code files may be absent on restricted firmware; see files.tsv.\n\n");
        List<InterfaceBundle.Source> sources = new ArrayList<>();
        List<ApplicationInfo> apps = new ArrayList<>(pm.getInstalledApplications(0));
        java.util.Collections.sort(apps, (a, b) -> {
            int order = Integer.compare(priority(a.packageName), priority(b.packageName));
            return order != 0 ? order : a.packageName.compareTo(b.packageName);
        });
        for (ApplicationInfo app : apps) {
            if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) continue;
            String label;
            try { label = pm.getApplicationLabel(app).toString(); }
            catch (RuntimeException ignored) { label = ""; }
            // Record all system package names to avoid mistaking a name-filter miss for no vendor stack.
            report.append("systemPackage=").append(app.packageName).append(" label=").append(label)
                .append(" path=").append(app.sourceDir).append('\n');
            if (!matches(app.packageName + " " + label)) continue;
            try {
                PackageInfo info = pm.getPackageInfo(app.packageName, PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS);
                report.append("candidateVersion=").append(info.versionName).append(" code=").append(info.versionCode).append('\n');
                if (info.requestedPermissions != null) report.append("requestedPermissions=")
                    .append(Arrays.toString(info.requestedPermissions)).append('\n');
                if (info.services != null) for (ServiceInfo service : info.services) {
                    report.append("service=").append(service.name).append(" exported=").append(service.exported)
                        .append(" enabled=").append(service.enabled).append(" permission=").append(service.permission)
                        .append(" process=").append(service.processName).append('\n');
                }
                add(sources, new File(app.sourceDir));
                if (app.splitSourceDirs != null) for (String path : app.splitSourceDirs) add(sources, new File(path));
                File parent = new File(app.sourceDir).getParentFile();
                if (parent != null) {
                    if (parent.getName().equals("app") || parent.getName().equals("priv-app")) {
                        // Older ROMs keep unrelated applications in one shared directory.
                        String stem = new File(app.sourceDir).getName().replaceFirst("\\.apk$", "");
                        add(sources, new File(parent, stem + ".odex"));
                        add(sources, new File(parent, stem + ".oat"));
                    } else codeFiles(parent, sources, report, 0, false);
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException error) {
                report.append("candidateReadFailure=").append(error.getClass().getSimpleName()).append('\n');
            }
        }
        for (String path : new String[]{"/system/framework", "/vendor/framework", "/system/lib", "/system/lib64", "/vendor/lib", "/vendor/lib64"}) {
            codeFiles(new File(path), sources, report, 0, true);
        }
        for (String name : new String[]{"framework.jar", "services.jar", "framework.odex", "services.odex", "arm/boot.oat", "arm64/boot.oat"}) {
            add(sources, new File("/system/framework", name));
        }
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File directory = base == null ? null : new File(base, "interface-bundles");
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs()) || !directory.canWrite()) {
            directory = new File(context.getFilesDir(), "interface-bundles");
        }
        return InterfaceBundle.write(directory, report.toString(), sources);
    }

    private static boolean matches(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return text.contains("bluetooth") || text.contains("蓝牙") || text.contains("ecarx") ||
            text.contains("gkui") || text.contains("anwsdk") || text.contains("phonelink") ||
            text.contains("btservice") || text.contains("btphone") || text.contains("btsdk");
    }

    private static int priority(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("bluetooth") || lower.contains("anwsdk") || lower.contains("btservice") ? 0 : 1;
    }

    private static void codeFiles(File directory, List<InterfaceBundle.Source> sources,
                                  StringBuilder report, int depth, boolean filter) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        File[] files = directory.listFiles();
        if (files == null) { report.append("directoryUnavailable=").append(directory).append('\n'); return; }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (filter && depth == 0) report.append("systemFile=").append(file).append('\n');
            if (file.isDirectory()) {
                if (depth < 2 && (name.equals("oat") || name.equals("arm") || name.equals("arm64"))) {
                    codeFiles(file, sources, report, depth + 1, filter);
                }
            } else if ((!filter || matches(name)) && (name.endsWith(".jar") || name.endsWith(".odex") ||
                    name.endsWith(".oat") || name.endsWith(".vdex") || name.endsWith(".so"))) add(sources, file);
        }
    }

    private static void add(List<InterfaceBundle.Source> sources, File file) throws IOException {
        if (sources.size() >= 256) return;
        String path = file.getCanonicalPath();
        // /data/app contains installed APK code; private app data directories are deliberately excluded.
        if (!(path.startsWith("/system/") || path.startsWith("/vendor/") || path.startsWith("/oem/") ||
                path.startsWith("/product/") || path.startsWith("/system_ext/") || path.startsWith("/data/app/"))) return;
        String entry = "firmware" + path;
        for (InterfaceBundle.Source source : sources) if (source.entry.equals(entry)) return;
        sources.add(new InterfaceBundle.Source(new File(path), entry));
    }
}
