package com.junjun.sideback;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 只读配置提供者：SystemUI 里的 hook 通过它拿到 JSON，App 本身不需要任何权限。
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.junjun.sideback.config";
    public static final String METHOD_GET = "get";
    public static final String KEY_JSON = "json";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (METHOD_GET.equals(method) && getContext() != null) {
            String raw = getContext()
                    .getSharedPreferences(BackFxConfig.PREFS, android.content.Context.MODE_PRIVATE)
                    .getString(BackFxConfig.PREF_KEY, null);
            result.putString(KEY_JSON, raw);
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
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
