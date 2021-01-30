package com.example.tool;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class AxolotlSQLiteOpenHelper extends SQLiteOpenHelper {
    public AxolotlSQLiteOpenHelper(@Nullable Context context, @Nullable String name) {
        super(context, name, null, 11);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
