package com.example.autosms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Har number sirf ek baar save hota hai (PRIMARY KEY) aur sent hone ke baad dobara nahi jata. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "sms.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE c(num TEXT PRIMARY KEY, msg TEXT, sent INTEGER DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun add(list: List<Pair<String, String>>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for ((num, msg) in list) {
                val cv = ContentValues().apply { put("num", num); put("msg", msg); put("sent", 0) }
                if (db.insertWithOnConflict("c", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }

    fun pending(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT num, msg FROM c WHERE sent=0 ORDER BY rowid", null).use {
            while (it.moveToNext()) out.add(it.getString(0) to (it.getString(1) ?: ""))
        }
        return out
    }

    fun markSent(num: String) {
        writableDatabase.execSQL("UPDATE c SET sent=1 WHERE num=?", arrayOf(num))
    }

    fun counts(): Pair<Int, Int> {
        readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(sent),0) FROM c", null).use {
            it.moveToFirst(); return it.getInt(0) to it.getInt(1)
        }
    }
}
