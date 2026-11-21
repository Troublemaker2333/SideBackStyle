package com.junjun.sideback;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 配置通道：把侧滑返回的设置交给 SystemUI。
 *
 * <p>SystemUI 读不到本应用的私有 SharedPreferences，也没有 WRITE_SECURE_SETTINGS，所以需要一个导出的
 * provider 作为通道。{@code BackFxConfig.queryProvider} 走 {@code query}，取第一列。
 *
 * <p>读的是 {@code backfx_local} —— 与 {@code BackFxConfig.publish()} 写的目标是同一个，也和
 * {@code readLocal()} 回退时读的是同一个，三处必须一致，否则 SystemUI 读到的和设置页显示的不是同一份。
 *
 * <p>同时保留 {@code call}：旧版本用的是 Binder 的 {@code call("get")} 返回 Bundle。
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.junjun.sideback.config";
    public static final String METHOD_GET = "get";
    public static final String KEY_JSON = "json";

    /** The prefs file `BackFxConfig.publish` writes and `readLocal` reads. */
    private static final String LOCAL_PREFS = "backfx_local";

    @Override
    public boolean onCreate() {
        return true;
    }

    private String readJson() {
        if (getContext() == null) {
            return null;
        }
        return getContext()
                .getSharedPreferences(LOCAL_PREFS, android.content.Context.MODE_PRIVATE)
                .getString(BackFxConfig.SETTINGS_KEY, null);
    }

    /** The channel {@code BackFxConfig.queryProvider} uses: a one-column cursor holding the JSON. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String json = readJson();
        if (json == null) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{KEY_JSON}, 1);
        cursor.addRow(new Object[]{json});
        return cursor;
    }

    /** The channel earlier versions used, kept so an in-place upgrade still reads its settings. */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (METHOD_GET.equals(method)) {
            result.putString(KEY_JSON, readJson());
        }
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
