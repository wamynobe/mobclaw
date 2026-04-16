package com.wamynobe.mobclaw.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wamynobe.mobclaw.ui.state.ProviderType
import com.wamynobe.mobclaw.ui.state.TaskHistoryEntry
import kotlin.time.Duration.Companion.milliseconds

class TaskHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "MobClawHistory.db"

        const val TABLE_HISTORY = "task_history"
        const val COLUMN_ID = "_id"
        const val COLUMN_TASK = "task"
        const val COLUMN_SUCCESS = "success"
        const val COLUMN_MESSAGE = "message"
        const val COLUMN_ITERATIONS = "iterations"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_PROVIDER = "provider"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TASK TEXT NOT NULL,
                $COLUMN_SUCCESS INTEGER NOT NULL,
                $COLUMN_MESSAGE TEXT NOT NULL,
                $COLUMN_ITERATIONS INTEGER NOT NULL,
                $COLUMN_DURATION_MS INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PROVIDER TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun insertTask(entry: TaskHistoryEntry) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TASK, entry.task)
            put(COLUMN_SUCCESS, if (entry.success) 1 else 0)
            put(COLUMN_MESSAGE, entry.message)
            put(COLUMN_ITERATIONS, entry.iterations)
            put(COLUMN_DURATION_MS, entry.duration.inWholeMilliseconds)
            put(COLUMN_TIMESTAMP, entry.timestamp)
            put(COLUMN_PROVIDER, entry.provider.name)
        }
        db.insert(TABLE_HISTORY, null, values)
        db.close()
    }

    fun getAllTasks(): List<TaskHistoryEntry> {
        val tasks = mutableListOf<TaskHistoryEntry>()
        val db = this.readableDatabase
        // Order by timestamp descending
        val cursor = db.query(
            TABLE_HISTORY, null, null, null, null, null, 
            "$COLUMN_TIMESTAMP DESC"
        )
        
        if (cursor.moveToFirst()) {
            do {
                val task = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TASK))
                val success = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SUCCESS)) == 1
                val message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE))
                val iterations = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ITERATIONS))
                val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION_MS))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val providerName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROVIDER))
                
                val provider = try {
                    ProviderType.valueOf(providerName)
                } catch (e: Exception) {
                    ProviderType.GEMINI // Fallback
                }
                
                tasks.add(
                    TaskHistoryEntry(
                        task = task,
                        success = success,
                        message = message,
                        iterations = iterations,
                        duration = durationMs.milliseconds,
                        timestamp = timestamp,
                        provider = provider
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return tasks
    }
}
