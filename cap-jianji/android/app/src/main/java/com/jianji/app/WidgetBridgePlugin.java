package com.jianji.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 桌面小组件桥接插件：
 * - updateStats(payload)：把首页/统计的金额写入 SharedPreferences 并刷新桌面小组件
 * - takeShortcut()：冷启动时 WebView 尚未就绪，快捷方式动作先暂存，页面就绪后拉取
 * - dispatchShortcut(action, injectNow)：由 MainActivity 在收到快捷方式 Intent 时调用
 */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    /** 小组件数据落盘位置，JianjiWidgetProvider 也读这里 */
    public static final String PREFS = "jianji_widget";
    /** 快捷方式动作在 Intent 中的 key，与 res/xml/shortcuts.xml 保持一致 */
    public static final String EXTRA_SHORTCUT = "com.jianji.app.extra.SHORTCUT";

    private static String pendingShortcut;

    @PluginMethod
    public void updateStats(PluginCall call) {
        Context ctx = getContext();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString("todayExpense", money(call.getDouble("todayExpense")))
                .putString("monthExpense", money(call.getDouble("monthExpense")))
                .putString("monthIncome", money(call.getDouble("monthIncome")))
                .putString("monthLabel", call.getString("monthLabel", ""))
                .putString("theme", call.getString("theme", "light"))
                .apply();
        JianjiWidgetProvider.refreshAll(ctx);
        call.resolve();
    }

    @PluginMethod
    public void takeShortcut(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("action", pendingShortcut);
        pendingShortcut = null;
        call.resolve(ret);
    }

    /**
     * 热启动（App 已在后台）时 WebView 已就绪，直接注入；冷启动则留给 takeShortcut 兜底，
     * 避免注入与拉取双重触发。
     */
    public void dispatchShortcut(String action, boolean injectNow) {
        if (!injectNow) {
            pendingShortcut = action;
            return;
        }
        pendingShortcut = null;
        try {
            WebView wv = getBridge() != null ? getBridge().getWebView() : null;
            if (wv != null) {
                wv.post(() -> wv.evaluateJavascript(
                        "window.__onShortcut&&window.__onShortcut(" + JSONObject.quote(action) + ")", null));
            }
        } catch (Exception ignored) {
            // 页面未就绪时静默失败，下次启动由 takeShortcut 兜底
        }
    }

    /** 金额格式化为千分位两位小数，null 视为 0 */
    private static String money(Double v) {
        return String.format(Locale.CHINA, "%,.2f", v == null ? 0d : v);
    }
}