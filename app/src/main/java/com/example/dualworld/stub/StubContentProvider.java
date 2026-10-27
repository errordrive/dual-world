package com.example.dualworld.stub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Pre-declared stub content provider placeholders for agent worker processes.
 */
public class StubContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
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

    public static class CP0 extends StubContentProvider {}
    public static class CP1 extends StubContentProvider {}

    // Compatibility aliases
    public static class P0 extends CP0 {}
    public static class P1 extends CP1 {}
    public static class P2 extends CP0 {}
    public static class P3 extends CP1 {}
}
