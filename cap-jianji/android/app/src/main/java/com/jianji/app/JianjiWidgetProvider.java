package com.jianji.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * 桌面小组件：今日支出 / 本月支出 / 本月收入。
 * 数据由 WidgetBridgePlugin 写入 SharedPreferences 后触发刷新；点击整块直达记账页。
 */
public class JianjiWidgetProvider extends AppWidgetProvider {

    /** 快捷方式 action，与 res/xml/shortcuts.xml 保持一致 */
    private static final String ACTION_SHORTCUT = "com.jianji.app.action.SHORTCUT";

    // 宣纸主题
    private static final int[] LIGHT = {0xFDFBF4, 0xA49C8B, 0x26221A, 0x6E6757, 0x3E7C5B};
    // 夜墨主题
    private static final int[] DARK = {0x221E17, 0x948B7B, 0xEDE7DA, 0xB5AC9C, 0x5FA97F};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        SharedPreferences sp = prefs(context);
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, sp));
        }
    }

    /** 金额变化后刷新所有已添加到桌面的实例（无实例时静默返回） */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, JianjiWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        SharedPreferences sp = prefs(context);
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, sp));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(WidgetBridgePlugin.PREFS, Context.MODE_PRIVATE);
    }

    private static RemoteViews build(Context context, SharedPreferences sp) {
        boolean dark = "dark".equals(sp.getString("theme", "light"));
        int[] c = dark ? DARK : LIGHT;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_jianji);
        views.setInt(R.id.widget_root, "setBackgroundResource",
                dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light);

        views.setTextViewText(R.id.widget_today, "¥" + sp.getString("todayExpense", "0.00"));
        views.setTextViewText(R.id.widget_month, sp.getString("monthLabel", ""));
        views.setTextViewText(R.id.widget_month_expense, context.getString(
                R.string.widget_month_expense_fmt, "¥" + sp.getString("monthExpense", "0.00")));
        views.setTextViewText(R.id.widget_month_income, context.getString(
                R.string.widget_month_income_fmt, "¥" + sp.getString("monthIncome", "0.00")));

        views.setTextColor(R.id.widget_today_label, c[1]);
        views.setTextColor(R.id.widget_month, c[1]);
        views.setTextColor(R.id.widget_today, c[2]);
        views.setTextColor(R.id.widget_month_expense, c[3]);
        views.setTextColor(R.id.widget_month_income, c[4]);

        // 点击整块 → 打开 App 并直达手动记账页
        Intent launch = new Intent(context, MainActivity.class);
        launch.setAction(ACTION_SHORTCUT);
        launch.putExtra(WidgetBridgePlugin.EXTRA_SHORTCUT, "expense");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        views.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(context, 1, launch, flags));

        return views;
    }
}