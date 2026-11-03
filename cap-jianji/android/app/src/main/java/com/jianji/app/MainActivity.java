package com.jianji.app;

import android.content.Intent;
import android.net.Uri;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BridgeActivity {

    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5MB 上限
    /** 长按图标快捷方式的 action，与 res/xml/shortcuts.xml 保持一致 */
    private static final String ACTION_SHORTCUT = "com.jianji.app.action.SHORTCUT";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SharedImportPlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        super.onCreate(savedInstanceState);
        handleIntent(getIntent(), false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, true);
    }

    /**
     * 处理系统分享/打开：
     * - JSON 文件：文件管理器"分享/打开" → 简记，自动读取内容并注入页面导入
     * - 图片：相册"发送" → 简记，复制到缓存后注入页面进入拍照识别
     *
     * @param warm true 表示从 onNewIntent 进入（页面已加载，可直接注入），冷启动为 false
     */
    private void handleIntent(Intent intent, boolean warm) {
        if (intent == null) return;
        String action = intent.getAction();

        // 长按图标快捷方式：记一笔 / 拍一张
        if (ACTION_SHORTCUT.equals(action)) {
            String shortcut = intent.getStringExtra(WidgetBridgePlugin.EXTRA_SHORTCUT);
            if (shortcut != null) {
                com.getcapacitor.PluginHandle handle = bridge.getPlugin("WidgetBridge");
                if (handle != null && handle.getInstance() instanceof WidgetBridgePlugin) {
                    ((WidgetBridgePlugin) handle.getInstance()).dispatchShortcut(shortcut, warm);
                }
            }
            return;
        }

        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_VIEW.equals(action)) return;

        String type = intent.getType();
        if (type == null) return;

        // 图片分享：相册 → 发送 → 简记
        if (Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) {
            handleImageSend(intent, type);
            return;
        }

        boolean accepted = type.equals("application/json")
                || type.equals("application/octet-stream")
                || type.endsWith("+json")
                || type.startsWith("text/");
        if (!accepted) return;

        String json = null;
        try {
            if (Intent.ACTION_SEND.equals(action)) {
                Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) {
                    json = readUri(stream);
                } else {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (text != null) json = text;
                }
            } else if (intent.getData() != null) {
                json = readUri(intent.getData());
            }
        } catch (Exception ignored) {
        }

        if (json != null && json.trim().startsWith("{")) {
            com.getcapacitor.PluginHandle handle = bridge.getPlugin("SharedImport");
            if (handle != null && handle.getInstance() instanceof SharedImportPlugin) {
                ((SharedImportPlugin) handle.getInstance()).receive(json);
            }
        }
    }

    /** 相册分享的图片：复制到应用缓存，交给插件通知页面 */
    private void handleImageSend(Intent intent, String type) {
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream == null) return;
        try {
            String ext = type.contains("png") ? ".png"
                    : type.contains("webp") ? ".webp"
                    : ".jpg";
            java.io.File out = new java.io.File(getCacheDir(), "shared_" + System.currentTimeMillis() + ext);
            java.io.InputStream in = getContentResolver().openInputStream(stream);
            if (in == null) return;
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            } finally {
                in.close();
            }
            com.getcapacitor.PluginHandle handle = bridge.getPlugin("SharedImport");
            if (handle != null && handle.getInstance() instanceof SharedImportPlugin) {
                ((SharedImportPlugin) handle.getInstance()).receiveImage(out.getAbsolutePath(), type);
            }
        } catch (Exception ignored) {
        }
    }

    private String readUri(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n, total = 0;
            while ((n = reader.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) break;
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}
