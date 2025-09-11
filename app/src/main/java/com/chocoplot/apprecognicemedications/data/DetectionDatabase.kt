package com.chocoplot.apprecognicemedications.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Database helper for storing medication detection results
 */
class DetectionDatabase(context: Context) : 
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val TAG = "DetectionDatabase"
        private const val DATABASE_NAME = "medication_detections.db"
        private const val DATABASE_VERSION = 1
        
        // Tables
        private const val TABLE_DETECTION_SESSIONS = "detection_sessions"
        private const val TABLE_DETECTED_ITEMS = "detected_items"
        
        // Session columns
        private const val COLUMN_SESSION_ID = "id"
        private const val COLUMN_SESSION_PHOTO_URI = "photo_uri"
        private const val COLUMN_SESSION_TIMESTAMP = "timestamp"
        private const val COLUMN_SESSION_TOTAL_ITEMS = "total_items"
        
        // Item columns
        private const val COLUMN_ITEM_ID = "id"
        private const val COLUMN_ITEM_SESSION_ID = "session_id"
        private const val COLUMN_ITEM_CLASS_NAME = "class_name"
        private const val COLUMN_ITEM_CONFIDENCE = "confidence"
        private const val COLUMN_ITEM_X1 = "x1"
        private const val COLUMN_ITEM_Y1 = "y1"
        private const val COLUMN_ITEM_X2 = "x2"
        private const val COLUMN_ITEM_Y2 = "y2"
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        val createSessionsTable = """
            CREATE TABLE $TABLE_DETECTION_SESSIONS (
                $COLUMN_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_PHOTO_URI TEXT NOT NULL,
                $COLUMN_SESSION_TIMESTAMP TEXT NOT NULL,
                $COLUMN_SESSION_TOTAL_ITEMS INTEGER NOT NULL
            )
        """.trimIndent()
        
        val createItemsTable = """
            CREATE TABLE $TABLE_DETECTED_ITEMS (
                $COLUMN_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ITEM_SESSION_ID INTEGER NOT NULL,
                $COLUMN_ITEM_CLASS_NAME TEXT NOT NULL,
                $COLUMN_ITEM_CONFIDENCE REAL NOT NULL,
                $COLUMN_ITEM_X1 REAL NOT NULL,
                $COLUMN_ITEM_Y1 REAL NOT NULL,
                $COLUMN_ITEM_X2 REAL NOT NULL,
                $COLUMN_ITEM_Y2 REAL NOT NULL,
                FOREIGN KEY ($COLUMN_ITEM_SESSION_ID) REFERENCES $TABLE_DETECTION_SESSIONS($COLUMN_SESSION_ID) ON DELETE CASCADE
            )
        """.trimIndent()
        
        db.execSQL(createSessionsTable)
        db.execSQL(createItemsTable)
        
        // Enable foreign key support
        db.execSQL("PRAGMA foreign_keys = ON")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database migrations here in future versions
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DETECTED_ITEMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DETECTION_SESSIONS")
        onCreate(db)
    }
    
    /**
     * Saves a new detection session with all detected items
     * @return The ID of the newly created session, or -1 if the operation failed
     */
    fun saveDetectionSession(photoUri: Uri, detections: List<BoundingBox>): Long {
        Log.d(TAG, "Saving detection session with ${detections.size} items")
        
        val db = writableDatabase
        var sessionId: Long = -1
        
        try {
            db.beginTransaction()
            
            // Create session record
            val timestamp = System.currentTimeMillis()
            val sessionValues = ContentValues().apply {
                put(COLUMN_SESSION_PHOTO_URI, photoUri.toString())
                put(COLUMN_SESSION_TIMESTAMP, timestamp)
                put(COLUMN_SESSION_TOTAL_ITEMS, detections.size)
            }
            
            sessionId = db.insert(TABLE_DETECTION_SESSIONS, null, sessionValues)
            
            // Insert detection items
            if (sessionId != -1L) {
                for (detection in detections) {
                    val itemValues = ContentValues().apply {
                        put(COLUMN_ITEM_SESSION_ID, sessionId)
                        put(COLUMN_ITEM_CLASS_NAME, detection.clsName)
                        put(COLUMN_ITEM_CONFIDENCE, detection.cnf)
                        put(COLUMN_ITEM_X1, detection.x1)
                        put(COLUMN_ITEM_Y1, detection.y1)
                        put(COLUMN_ITEM_X2, detection.x2)
                        put(COLUMN_ITEM_Y2, detection.y2)
                    }
                    
                    val itemId = db.insert(TABLE_DETECTED_ITEMS, null, itemValues)
                    if (itemId == -1L) {
                        Log.e(TAG, "Failed to insert detection item for session $sessionId")
                    }
                }
            }
            
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving detection session", e)
            sessionId = -1L
        } finally {
            db.endTransaction()
        }
        
        return sessionId
    }
    
    /**
     * Gets all detection sessions in descending order by timestamp
     */
    fun getAllDetectionSessions(): List<DetectionSession> {
        val sessions = mutableListOf<DetectionSession>()
        val db = readableDatabase
        
        val query = """
            SELECT * FROM $TABLE_DETECTION_SESSIONS
            ORDER BY $COLUMN_SESSION_TIMESTAMP DESC
        """.trimIndent()
        
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID))
                val photoUriStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_PHOTO_URI))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SESSION_TIMESTAMP))
                val totalItems = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SESSION_TOTAL_ITEMS))
                
                sessions.add(
                    DetectionSession(
                        id = id,
                        photoUri = Uri.parse(photoUriStr),
                        timestamp = timestamp,
                        totalItems = totalItems
                    )
                )
            }
        }
        
        return sessions
    }
    
    /**
     * Finds a detection session for a specific photo URI
     * @return The session if found, null otherwise
     */
    fun findSessionByPhotoUri(photoUri: Uri): DetectionSession? {
        val db = readableDatabase
        
        val query = """
            SELECT * FROM $TABLE_DETECTION_SESSIONS
            WHERE $COLUMN_SESSION_PHOTO_URI = ?
            ORDER BY $COLUMN_SESSION_TIMESTAMP DESC
            LIMIT 1
        """.trimIndent()
        
        db.rawQuery(query, arrayOf(photoUri.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID))
                val photoUriStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_PHOTO_URI))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SESSION_TIMESTAMP))
                val totalItems = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SESSION_TOTAL_ITEMS))
                
                return DetectionSession(
                    id = id,
                    photoUri = Uri.parse(photoUriStr),
                    timestamp = timestamp,
                    totalItems = totalItems
                )
            }
        }
        
        return null
    }
    
    /**
     * Gets all detection items for a specific session
     */
    fun getDetectionItems(sessionId: Long): List<DetectedItem> {
        val items = mutableListOf<DetectedItem>()
        val db = readableDatabase
        
        val query = """
            SELECT * FROM $TABLE_DETECTED_ITEMS
            WHERE $COLUMN_ITEM_SESSION_ID = ?
        """.trimIndent()
        
        db.rawQuery(query, arrayOf(sessionId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ITEM_ID))
                val className = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ITEM_CLASS_NAME))
                val confidence = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ITEM_CONFIDENCE))
                val x1 = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ITEM_X1))
                val y1 = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ITEM_Y1))
                val x2 = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ITEM_X2))
                val y2 = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ITEM_Y2))
                
                items.add(
                    DetectedItem(
                        id = id,
                        sessionId = sessionId,
                        className = className,
                        confidence = confidence,
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2
                    )
                )
            }
        }
        
        return items
    }
    
    /**
     * Gets a summary of detected medication counts for a specific session
     */
    fun getDetectionSummary(sessionId: Long): Map<String, Int> {
        val summary = mutableMapOf<String, Int>()
        val db = readableDatabase
        
        val query = """
            SELECT $COLUMN_ITEM_CLASS_NAME, COUNT(*) as count
            FROM $TABLE_DETECTED_ITEMS
            WHERE $COLUMN_ITEM_SESSION_ID = ?
            GROUP BY $COLUMN_ITEM_CLASS_NAME
            ORDER BY count DESC
        """.trimIndent()
        
        db.rawQuery(query, arrayOf(sessionId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val className = cursor.getString(0)
                val count = cursor.getInt(1)
                summary[className] = count
            }
        }
        
        return summary
    }
    
    /**
     * Deletes a detection session and all its items (using CASCADE)
     * @return Number of sessions deleted (should be 0 or 1)
     */
    fun deleteDetectionSession(sessionId: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_DETECTION_SESSIONS,
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString())
        )
    }
    
    /**
     * Clears all detection sessions and items from the database
     * @return Number of sessions deleted
     */
    fun clearAllDetections(): Int {
        val db = writableDatabase
        var deletedSessions = 0
        
        try {
            db.beginTransaction()
            
            // Delete all items first (though CASCADE should handle this)
            db.delete(TABLE_DETECTED_ITEMS, null, null)
            
            // Delete all sessions
            deletedSessions = db.delete(TABLE_DETECTION_SESSIONS, null, null)
            
            db.setTransactionSuccessful()
            Log.d(TAG, "Cleared $deletedSessions detection sessions from database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all detections", e)
        } finally {
            db.endTransaction()
        }
        
        return deletedSessions
    }
}

/**
 * Data class representing a detection session
 */
data class DetectionSession(
    val id: Long,
    val photoUri: Uri,
    val timestamp: Long,
    val totalItems: Int
)

/**
 * Data class representing a detected medication item
 */
data class DetectedItem(
    val id: Long,
    val sessionId: Long,
    val className: String,
    val confidence: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)