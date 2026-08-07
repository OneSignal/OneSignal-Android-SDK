package com.onesignal.notifications.internal.ingress

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationFormatHelper
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.open.INotificationOpenedProcessor
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import org.json.JSONObject

internal object NotificationIngress {
    private const val DRAIN_WORK_NAME = "OneSignalNotificationIngressDrain"

    @Volatile
    internal var drainSchedulerForTest: ((Context) -> Unit)? = null

    fun persistFcm(
        context: Context,
        intent: Intent,
        bundle: Bundle,
    ): Boolean {
        val notificationId = NotificationFormatHelper.getOSNotificationIdFromJson(BundleCodec.toJson(bundle)) ?: return false
        val record =
            IngressRecord(
                id = "fcm:$notificationId",
                kind = IngressKind.FCM,
                action = intent.action,
                payload = BundleCodec.encode(bundle),
                createdAtMs = System.currentTimeMillis(),
            )
        IngressStore.get(context).put(record)
        scheduleDrainBestEffort(context)
        return true
    }

    fun persistDismiss(
        context: Context,
        intent: Intent,
    ) {
        val bundle = intent.extras ?: Bundle()
        val notificationId = bundle.getInt(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0)
        val summary = bundle.getString("summary").orEmpty()
        val payload = BundleCodec.encode(bundle)
        val record =
            IngressRecord(
                id = "dismiss:$notificationId:$summary:${payload.hashCode()}",
                kind = IngressKind.DISMISS,
                action = intent.action,
                payload = payload,
                createdAtMs = System.currentTimeMillis(),
            )
        IngressStore.get(context).put(record)
        scheduleDrainBestEffort(context)
    }

    fun enqueueRestore(context: Context) {
        NotificationRestoreWorkManager().beginEnqueueingWork(context, true)
    }

    fun scheduleDrain(context: Context) {
        drainSchedulerForTest?.let {
            it(context)
            return
        }
        val request = OneTimeWorkRequest.Builder(NotificationIngressDrainWorker::class.java).build()
        OSWorkManagerHelper.getInstance(context.applicationContext)
            .enqueueUniqueWork(DRAIN_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleDrainBestEffort(context: Context) {
        if (drainSchedulerForTest != null) {
            try {
                scheduleDrain(context)
            } catch (e: Exception) {
                Logging.warn("Notification ingress persisted; drain scheduling will retry on next startup", e)
            }
            return
        }
        OneSignalDispatchers.launchOnIO {
            try {
                scheduleDrain(context)
            } catch (e: Exception) {
                Logging.warn("Notification ingress persisted; drain scheduling will retry on next startup", e)
            }
        }
    }

    internal fun pendingCountForTest(context: Context): Int = IngressStore.get(context).count()

    internal fun resetForTest(context: Context) {
        drainSchedulerForTest = null
        IngressStore.get(context).clear()
    }
}

internal class NotificationIngressDrainStarter(
    private val applicationService: IApplicationService,
) : IStartableService {
    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        try {
            NotificationIngress.scheduleDrain(applicationService.appContext)
        } catch (e: Exception) {
            Logging.warn("Notification ingress startup drain scheduling failed", e)
        }
    }
}

internal class NotificationIngressDrainWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val store = IngressStore.get(applicationContext)
        if (!OneSignal.initWithContext(applicationContext)) return Result.retry()

        return try {
            for (record in store.list()) {
                when (record.kind) {
                    IngressKind.FCM -> processFcm(record)
                    IngressKind.DISMISS -> processDismiss(record)
                }
                store.delete(record.id)
            }
            Result.success()
        } catch (e: Exception) {
            Logging.error("Notification ingress drain failed", e)
            Result.retry()
        }
    }

    private fun processFcm(record: IngressRecord) {
        val bundle = BundleCodec.decode(record.payload)
        OneSignal.getService<INotificationBundleProcessor>()
            .processBundleFromReceiver(applicationContext, bundle)
    }

    private suspend fun processDismiss(record: IngressRecord) {
        val intent = Intent(record.action).putExtras(BundleCodec.decode(record.payload))
        OneSignal.getService<INotificationOpenedProcessor>()
            .processFromContext(applicationContext, intent)
    }
}

private enum class IngressKind {
    FCM,
    DISMISS,
}

private data class IngressRecord(
    val id: String,
    val kind: IngressKind,
    val action: String?,
    val payload: String,
    val createdAtMs: Long,
)

private class IngressStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                action TEXT,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        database.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(database)
    }

    fun put(record: IngressRecord) {
        val values =
            ContentValues().apply {
                put("id", record.id)
                put("kind", record.kind.name)
                put("action", record.action)
                put("payload", record.payload)
                put("created_at", record.createdAtMs)
            }
        check(writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Unable to persist notification ingress"
        }
    }

    fun list(): List<IngressRecord> {
        val records = mutableListOf<IngressRecord>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "kind", "action", "payload", "created_at"),
            null,
            null,
            null,
            null,
            "created_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records +=
                    IngressRecord(
                        id = cursor.getString(ID_COLUMN_INDEX),
                        kind = IngressKind.valueOf(cursor.getString(KIND_COLUMN_INDEX)),
                        action = cursor.getString(ACTION_COLUMN_INDEX),
                        payload = cursor.getString(PAYLOAD_COLUMN_INDEX),
                        createdAtMs = cursor.getLong(CREATED_AT_COLUMN_INDEX),
                    )
            }
        }
        return records
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "OneSignalIngress.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE = "notification_ingress"
        private const val ID_COLUMN_INDEX = 0
        private const val KIND_COLUMN_INDEX = 1
        private const val ACTION_COLUMN_INDEX = 2
        private const val PAYLOAD_COLUMN_INDEX = 3
        private const val CREATED_AT_COLUMN_INDEX = 4

        @Volatile
        private var instance: IngressStore? = null

        fun get(context: Context): IngressStore =
            instance ?: synchronized(this) {
                instance ?: IngressStore(context).also { instance = it }
            }
    }
}

@Suppress("DEPRECATION")
private object BundleCodec {
    private const val TYPE = "type"
    private const val VALUE = "value"

    fun encode(bundle: Bundle): String {
        val root = JSONObject()
        for (key in bundle.keySet()) {
            val value = bundle[key]
            val encoded = JSONObject()
            when (value) {
                is Boolean -> encoded.put(TYPE, "boolean").put(VALUE, value)
                is Int -> encoded.put(TYPE, "int").put(VALUE, value)
                is Long -> encoded.put(TYPE, "long").put(VALUE, value)
                is Double -> encoded.put(TYPE, "double").put(VALUE, value)
                else -> encoded.put(TYPE, "string").put(VALUE, value?.toString())
            }
            root.put(key, encoded)
        }
        return root.toString()
    }

    fun decode(encoded: String): Bundle {
        val root = JSONObject(encoded)
        val bundle = Bundle()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = root.getJSONObject(key)
            when (value.getString(TYPE)) {
                "boolean" -> bundle.putBoolean(key, value.getBoolean(VALUE))
                "int" -> bundle.putInt(key, value.getInt(VALUE))
                "long" -> bundle.putLong(key, value.getLong(VALUE))
                "double" -> bundle.putDouble(key, value.getDouble(VALUE))
                else -> bundle.putString(key, if (value.isNull(VALUE)) null else value.getString(VALUE))
            }
        }
        return bundle
    }

    fun toJson(bundle: Bundle): JSONObject {
        val json = JSONObject()
        for (key in bundle.keySet()) {
            json.put(key, bundle[key])
        }
        return json
    }
}
