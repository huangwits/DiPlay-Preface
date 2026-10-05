package com.shihab.diplay.diagnostics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** API 22 diagnostic host. No CarPlay session, bundled identity, network or broad storage permission. */
public final class DiagnosticActivity extends Activity {
    private static final UUID IAP2_SERVICE = UUID.fromString("00000000-deca-fade-deca-deafdecacafe");
    private static final int REQUEST_BLUETOOTH = 31;
    private static final int REQUEST_SAVE_BUNDLE = 32;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "diplay-probe-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private TextView report;
    private String vendorSnapshot = "";
    private boolean vendorScanRunning;
    private Button testButton;
    private Button cancelButton;
    private String snapshot = "";
    private String testResult = "尚未测试 RFCOMM。";
    private ProbeAttempt attempt;
    private int generation;
    private boolean destroyed;
    private Button exportButton;
    private Thread exportWorker;
    private java.io.File lastBundle;
    private java.io.File pendingSave;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(12), dp(16), dp(12));
        column.setBackgroundColor(Color.WHITE);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("DiPlay · 老车机蓝牙诊断");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        column.addView(title);
        TextView help = new TextView(this);
        help.setText("停车后操作。先退出 DiPlay 和其他投屏应用，再测试已配对的 iPhone。"
            + "本工具只检查蓝牙通道，不能启动 CarPlay；测试结束会关闭通道。配对记录不会被删除。"
            + "\n若系统未提供蓝牙适配器，直接导出 E01 蓝牙适配资料，无需先开启蓝牙，也无需连接电脑。");
        help.setTextSize(16);
        help.setTextColor(Color.DKGRAY);
        column.addView(help);
        addButton(column, "刷新系统与蓝牙状态", v -> refresh());
        addButton(column, "识别 E01 原厂蓝牙应用（无需电脑）", v -> scanVendorApps());
        exportButton = addButton(column, "导出 E01 蓝牙适配资料 ZIP", v -> exportInterfaces());
        addButton(column, "打开已导出的资料", v -> showBundle());
        addButton(column, "请求开启系统蓝牙", v -> enableBluetooth());
        addButton(column, "打开车机蓝牙设置", v -> openSettings());
        testButton = addButton(column, "选择已配对 iPhone，测试 RFCOMM（15 秒）", v -> choosePhone());
        cancelButton = addButton(column, "取消当前测试", v -> cancelAttempt());
        cancelButton.setEnabled(false);
        addButton(column, "复制诊断报告", v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("DiPlay 蓝牙诊断", report.getText()));
            Toast.makeText(this, "报告已复制；未复制设备名称或蓝牙地址", Toast.LENGTH_SHORT).show();
        });
        report = new TextView(this);
        report.setTextSize(16);
        report.setTextColor(Color.BLACK);
        report.setTextIsSelectable(true);
        report.setPadding(0, dp(12), 0, dp(24));
        column.addView(report);
        if (savedInstanceState != null) {
            testResult = savedInstanceState.getString("testResult", testResult);
            if (savedInstanceState.getBoolean("wasTesting")) testResult = "测试已因页面重建取消，请重新测试。";
            lastBundle = restoredBundle(savedInstanceState.getString("lastBundle"));
            pendingSave = restoredBundle(savedInstanceState.getString("pendingSave"));
        }
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (report != null) refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("testResult", testResult);
        state.putBoolean("wasTesting", attempt != null);
        if (lastBundle != null) state.putString("lastBundle", lastBundle.getPath());
        if (pendingSave != null) state.putString("pendingSave", pendingSave.getPath());
    }

    private Button addButton(LinearLayout column, String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setOnClickListener(click);
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    @SuppressWarnings("deprecation")
    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter value = manager == null ? null : manager.getAdapter();
        return value != null ? value : BluetoothAdapter.getDefaultAdapter();
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ensureBluetoothPermission() {
        if (hasBluetoothPermission()) return true;
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
        return false;
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == REQUEST_BLUETOOTH) {
            refresh();
            Toast.makeText(this, hasBluetoothPermission() ? "权限已授予，请再次点击操作" : "未授予蓝牙权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void refresh() {
        StringBuilder out = new StringBuilder("DiPlay Bluetooth diagnostics 0.3-e01-interface\n");
        out.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("硬件：").append(Build.HARDWARE).append(" / ").append(Build.BOARD).append('\n');
        out.append("固件：").append(Build.DISPLAY).append('\n');
        out.append("ABI：");
        for (String abi : Build.SUPPORTED_ABIS) out.append(abi).append(' ');
        out.append('\n');
        out.append("蓝牙硬件声明：").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).append('\n');
        for (String name : new String[]{"com.shihab.diplay", "com.shihab.diplay.e01", "com.shihab.diplay.e01legacy", "com.shihab.diplay.hudtest", "com.shilapi.xcertplay"}) {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(name, 0);
                out.append("已装应用：").append(name).append(" version=").append(info.versionName)
                    .append(" code=").append(info.versionCode).append('\n');
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        out.append("注：仅检查上述已知包名；其他分支可能使用不同包名。\n");
        if (!hasBluetoothPermission()) {
            out.append("状态：缺少附近设备 / 蓝牙连接权限。点击测试或开启按钮可申请。\n");
        } else {
            try {
                BluetoothAdapter bt = adapter();
                if (bt == null) {
                    out.append("状态：Android 未向本应用提供标准蓝牙适配器。\n")
                        .append("车机自带蓝牙电话仍可能可用；本应用无法据此建立标准 RFCOMM。请点击“导出 E01 蓝牙适配资料 ZIP”，用于定位原厂连接接口。\n");
                } else {
                    int state = bt.getState();
                    out.append("蓝牙状态：").append(stateName(state)).append(" (").append(state).append(")\n");
                    if (state == BluetoothAdapter.STATE_ON) {
                        Set<BluetoothDevice> bonded = bt.getBondedDevices();
                        out.append("系统提供的配对设备数：").append(bonded == null ? 0 : bonded.size()).append('\n');
                        if (bonded == null || bonded.isEmpty()) out.append("请先在车机设置配对 iPhone。若车机显示已配对而这里仍为 0，配对列表可能由厂商接口独立管理。\n");
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        out.append("请先请求开启系统蓝牙；开启失败不等于 RFCOMM 连接失败。\n");
                    } else out.append("系统正在切换蓝牙状态，请稍后刷新。\n");
                }
            } catch (SecurityException failure) {
                out.append("蓝牙权限被系统拒绝：").append(safeFailure(failure)).append('\n');
            } catch (RuntimeException failure) {
                out.append("读取蓝牙失败：").append(safeFailure(failure)).append('\n');
            }
        }
        snapshot = out.toString();
        renderReport();
    }

    private void renderReport() { report.setText(snapshot + vendorSnapshot + "\nRFCOMM 测试：\n" + testResult); }

    private void exportInterfaces() {
        if (exportWorker != null) return;
        refresh();
        final String diagnostic = snapshot;
        exportButton.setEnabled(false);
        exportButton.setText("正在导出原厂蓝牙资料，请稍候…");
        final android.content.Context app = getApplicationContext();
        exportWorker = new Thread(() -> {
            java.io.File bundle = null;
            String error = null;
            try { bundle = VendorInterfaceCollector.collect(app, diagnostic); }
            catch (IOException | RuntimeException failure) { error = safeFailure(failure); }
            final java.io.File completed = bundle;
            final String failure = error;
            main.post(() -> {
                exportWorker = null;
                if (destroyed) return;
                exportButton.setEnabled(true);
                exportButton.setText("导出 E01 蓝牙适配资料 ZIP");
                if (completed != null) {
                    lastBundle = completed;
                    showBundle();
                } else new AlertDialog.Builder(this).setTitle("导出未完成").setMessage(failure)
                    .setPositiveButton("关闭", null).show();
            });
        }, "e01-interface-export");
        exportWorker.setDaemon(true);
        exportWorker.start();
    }

    private java.io.File restoredBundle(String path) {
        if (path == null) return null;
        try {
            java.io.File file = new java.io.File(path).getCanonicalFile();
            if (!file.isFile() || !file.getName().startsWith("E01-bluetooth-") || !file.getName().endsWith(".zip")) return null;
            java.io.File external = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            for (java.io.File base : new java.io.File[]{getFilesDir(), external}) {
                if (base != null && file.getParentFile().equals(new java.io.File(base, "interface-bundles").getCanonicalFile())) return file;
            }
        } catch (IOException ignored) { }
        return null;
    }

    private void showBundle() {
        if (lastBundle == null || !lastBundle.isFile()) {
            Toast.makeText(this, "请先点击“导出 E01 蓝牙适配资料 ZIP”", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("蓝牙适配资料已生成")
            .setMessage("请把 ZIP 发回以便适配这台车机的蓝牙接口。它包含固件信息及可读取的原厂程序／系统库，不包含联系人或配对记录。\n\n文件位置：\n" + lastBundle.getAbsolutePath()
                + "\n\n如车机没有文件选择器，可用原厂文件管理器从以上位置复制到 U 盘。")
            .setPositiveButton("保存到 U 盘／文件", (dialog, which) -> saveBundle())
            .setNeutralButton("分享 ZIP", (dialog, which) -> shareBundle())
            .setNegativeButton("关闭", null).show();
    }

    private void shareBundle() {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".bundles", lastBundle);
            Intent send = new Intent(Intent.ACTION_SEND).setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("E01 Bluetooth interface bundle", uri));
            startActivity(Intent.createChooser(send, "发送 E01 蓝牙资料"));
        } catch (RuntimeException error) {
            Toast.makeText(this, "没有可用的分享应用；请用文件管理器复制 ZIP。", Toast.LENGTH_LONG).show();
        }
    }

    private void saveBundle() {
        pendingSave = lastBundle;
        try {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, lastBundle.getName()), REQUEST_SAVE_BUNDLE);
        } catch (RuntimeException error) {
            pendingSave = null;
            Toast.makeText(this, "车机没有可用的文件选择器；请从显示的路径复制 ZIP。", Toast.LENGTH_LONG).show();
            showBundle();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_SAVE_BUNDLE) return;
        final java.io.File source = pendingSave;
        pendingSave = null;
        if (result != RESULT_OK || data == null || data.getData() == null || source == null) return;
        final android.net.Uri destination = data.getData();
        final android.content.ContentResolver resolver = getContentResolver();
        Thread writer = new Thread(() -> {
            String message;
            try (java.io.InputStream in = new java.io.FileInputStream(source);
                 java.io.OutputStream out = resolver.openOutputStream(destination, "w")) {
                if (out == null) throw new IOException("Selected location is unavailable");
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                message = "ZIP 已保存到所选位置。";
            } catch (IOException | RuntimeException error) { message = "保存失败，原 ZIP 仍在：" + source.getAbsolutePath(); }
            final String text = message;
            main.post(() -> { if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); });
        }, "e01-interface-save");
        writer.setDaemon(true);
        writer.start();
    }

    private void scanVendorApps() {
        if (vendorScanRunning) return;
        vendorScanRunning = true;
        vendorSnapshot = "\n正在读取原厂应用信息……\n";
        renderReport();
        Thread worker = new Thread(() -> {
            StringBuilder out = new StringBuilder("\n原厂蓝牙接口线索（只读）：\n");
            try {
                java.util.List<String> candidates = new java.util.ArrayList<>();
                PackageManager pm = getPackageManager();
                for (android.content.pm.ApplicationInfo app : pm.getInstalledApplications(0)) {
                    if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) continue;
                    String label;
                    try { label = pm.getApplicationLabel(app).toString(); }
                    catch (RuntimeException ignored) { label = ""; }
                    String searchable = (app.packageName + " " + label).toLowerCase(java.util.Locale.ROOT);
                    if (!(searchable.contains("bluetooth") || searchable.contains("蓝牙") ||
                        searchable.contains("ecarx") || searchable.contains("gkui") ||
                        searchable.contains("anw") || searchable.contains("phonelink"))) continue;
                    String version;
                    try { version = pm.getPackageInfo(app.packageName, 0).versionName; }
                    catch (PackageManager.NameNotFoundException ignored) { version = "unknown"; }
                    candidates.add(label + "\n  " + app.packageName + " / " + version + "\n  " + app.sourceDir);
                }
                java.util.Collections.sort(candidates);
                if (candidates.isEmpty()) out.append("未找到名称匹配的系统应用，不代表厂商接口不存在。\n");
                for (String candidate : candidates) out.append(candidate).append('\n');
                out.append("相关 framework 文件：\n");
                String[] names = new java.io.File("/system/framework").list();
                int found = 0;
                if (names != null) {
                    java.util.Arrays.sort(names);
                    for (String name : names) {
                        String lower = name.toLowerCase(java.util.Locale.ROOT);
                        if (lower.contains("bluetooth") || lower.contains("ecarx") ||
                            lower.contains("gkui") || lower.contains("anw")) {
                            out.append(name).append('\n'); found++;
                        }
                    }
                }
                if (found == 0) out.append("未发现匹配项或目录不可读。\n");
                out.append("这些名称仅用于定位接口，不证明 RFCOMM 可用；未启动原厂服务或更改开关。\n");
                if (Build.VERSION.SDK_INT >= 30) out.append("Android 11+ 可能限制可见应用；安卓 5.1 不受此限制。\n");
            } catch (RuntimeException error) {
                out.append("读取失败：").append(error.getClass().getSimpleName()).append('\n');
            }
            String result = out.toString();
            main.post(() -> {
                vendorScanRunning = false;
                if (isFinishing() || isDestroyed()) return;
                vendorSnapshot = result;
                renderReport();
                new AlertDialog.Builder(this).setTitle("E01 原厂蓝牙信息")
                    .setMessage(result).setPositiveButton("关闭", null).show();
            });
        }, "e01-vendor-metadata");
        worker.setDaemon(true);
        worker.start();
    }

    private static String stateName(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON: return "已开启";
            case BluetoothAdapter.STATE_OFF: return "已关闭";
            case BluetoothAdapter.STATE_TURNING_ON: return "正在开启";
            case BluetoothAdapter.STATE_TURNING_OFF: return "正在关闭";
            default: return "未知";
        }
    }

    private void enableBluetooth() {
        if (!ensureBluetoothPermission()) return;
        try {
            BluetoothAdapter bt = adapter();
            if (bt == null) { refresh(); return; }
            if (bt.isEnabled()) { refresh(); return; }
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } catch (SecurityException failure) {
            testResult = "系统拒绝开启蓝牙：" + safeFailure(failure);
            refresh();
        } catch (RuntimeException failure) {
            testResult = "系统开启请求不可用：" + safeFailure(failure) + "\n请使用车机自带蓝牙设置。";
            refresh();
        }
    }

    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        catch (RuntimeException failure) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException ignored) { Toast.makeText(this, "请手动打开车机设置", Toast.LENGTH_LONG).show(); }
        }
    }

    private void choosePhone() {
        if (attempt != null || !ensureBluetoothPermission()) return;
        try {
            BluetoothAdapter bt = adapter();
            if (bt == null || !bt.isEnabled()) { refresh(); return; }
            List<BluetoothDevice> devices = new ArrayList<>(bt.getBondedDevices());
            if (devices.isEmpty()) { refresh(); return; }
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice device = devices.get(i);
                String name = device.getName();
                labels[i] = (name == null ? "已配对设备" : name) + " · " + device.getAddress().substring(12);
            }
            new AlertDialog.Builder(this).setTitle("选择要测试的 iPhone")
                .setItems(labels, (dialog, index) -> startProbe(bt, devices.get(index)))
                .setNegativeButton("取消", null).show();
        } catch (SecurityException failure) {
            testResult = "系统拒绝读取配对设备：" + safeFailure(failure);
            refresh();
        } catch (RuntimeException failure) {
            testResult = "无法读取配对设备：" + safeFailure(failure);
            refresh();
        }
    }

    private void startProbe(BluetoothAdapter bt, BluetoothDevice device) {
        if (attempt != null) return;
        final int run = ++generation;
        final long started = android.os.SystemClock.elapsedRealtime();
        testResult = "正在打开 iPhone 的 iAP2 RFCOMM 服务，最长等待 15 秒。";
        testButton.setEnabled(false);
        cancelButton.setEnabled(true);
        renderReport();
        ProbeAttempt probe = new ProbeAttempt(result -> main.post(() -> {
            if (destroyed || run != generation) return;
            attempt = null;
            testButton.setEnabled(true);
            cancelButton.setEnabled(false);
            long elapsed = android.os.SystemClock.elapsedRealtime() - started;
            switch (result.outcome) {
                case CONNECTED:
                    testResult = "RFCOMM 连接成功；通道已关闭。\n这说明本次标准蓝牙通道可连接；尚未测试 iAP2/MFi 认证、Wi-Fi 接管或 CarPlay 画面。";
                    break;
                case TIMED_OUT:
                    testResult = "RFCOMM 连接超时（15 秒）。\n仅配对成功不能证明 iPhone 的 CarPlay 通道可用。请退出其他投屏应用、解锁 iPhone，检查蓝牙配对与 CarPlay 许可后重试；也可单独测试 USB。此结果不能独自确定是车机还是手机原因。";
                    break;
                case CANCELLED: testResult = "测试已取消，通道已关闭。"; break;
                default:
                    testResult = "RFCOMM 连接失败：" + safeFailure(result.failure)
                        + "\n请保留此报告及原 DiPlay 的完整报错，以区分系统接口限制、通道连接和后续认证问题。";
            }
            testResult += "\n耗时：" + elapsed + " ms";
            refresh();
        }));
        attempt = probe;
        timer.schedule(probe::timeout, 15, TimeUnit.SECONDS);
        Thread worker = new Thread(() -> probe.run(() -> {
            // Android 12+ requires SCAN to cancel discovery. Do not request it just for this probe.
            cancelLegacyDiscovery(bt);
            final BluetoothSocket socket = device.createRfcommSocketToServiceRecord(IAP2_SERVICE);
            return new ProbeAttempt.Connection() {
                @Override public void connect() throws IOException { socket.connect(); }
                @Override public void close() throws IOException { socket.close(); }
            };
        }), "diplay-rfcomm-probe");
        worker.setDaemon(true);
        worker.start();
    }

    private void cancelAttempt() {
        ProbeAttempt current = attempt;
        if (current != null) current.cancel();
    }

    @SuppressLint("MissingPermission") // API <31 uses the manifest BLUETOOTH_ADMIN permission.
    private static void cancelLegacyDiscovery(BluetoothAdapter bt) {
        if (Build.VERSION.SDK_INT < 31) {
            try { bt.cancelDiscovery(); } catch (RuntimeException ignored) { }
        }
    }

    static String safeFailure(Exception failure) {
        if (failure == null) return "未知错误";
        String message = failure.getMessage();
        if (message == null) return failure.getClass().getSimpleName();
        message = message.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}", "[蓝牙地址]")
            .replaceAll("[\\r\\n]+", " ");
        if (message.length() > 240) message = message.substring(0, 240);
        return failure.getClass().getSimpleName() + ": " + message;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        cancelAttempt();
        timer.shutdownNow();
        if (exportWorker != null) exportWorker.interrupt();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
