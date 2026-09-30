package com.example.oslaucher.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CachedApp(val packageName: String, val name: String)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE_APPS (
                $COLUMN_PACKAGE TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_APPS")
        onCreate(db)
    }

    fun replaceApps(apps: List<CachedApp>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_APPS, null, null)
            apps.forEach { app ->
                val values = ContentValues().apply {
                    put(COLUMN_PACKAGE, app.packageName)
                    put(COLUMN_NAME, app.name)
                }
                db.insertWithOnConflict(TABLE_APPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCachedApps(): List<CachedApp> {
        val apps = mutableListOf<CachedApp>()
        readableDatabase.query(
            TABLE_APPS,
            arrayOf(COLUMN_PACKAGE, COLUMN_NAME),
            null,
            null,
            null,
            null,
            "$COLUMN_NAME COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                apps += CachedApp(cursor.getString(0), cursor.getString(1))
            }
        }
        return apps
    }

    private companion object {
        const val DATABASE_NAME = "installed_apps.db"
        const val DATABASE_VERSION = 1
        const val TABLE_APPS = "installed_apps"
        const val COLUMN_PACKAGE = "package_name"
        const val COLUMN_NAME = "app_name"
    }
}