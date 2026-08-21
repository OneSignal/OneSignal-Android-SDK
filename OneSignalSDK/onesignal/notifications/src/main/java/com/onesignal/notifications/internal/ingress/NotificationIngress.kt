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
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
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
        scheduleDrainDurably(context)
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
        scheduleDrainDurably(context)
    }

    fun enqueueRestore(context: Context) {
        NotificationRestoreWorkManager().beginEnqueueingWork(context, true)
    }

    fun scheduleDrain(context: Context) {
        enqueueDrain(context)
    }

    private fun scheduleDrainDurably(context: Context) {
        enqueueDrain(context)?.result?.get()
    }

    private fun enqueueDrain(context: Context): Operation? {
        drainSchedulerForTest?.let {
            it(context)
            return null
        }
        val request = OneTimeWorkRequest.Builder(NotificationIngressDrainWorker::class.java).build()
        return OSWorkManagerHelper.getInstance(context.applicationContext)
            .enqueueUniqueWork(DRAIN_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    internal fun pendingCountForTest(context: Context): Int = IngressStore.get(context).count()

    internal fun resetForTest(context: Context) {
        drainSchedulerForTest = null
        IngressStore.get(context).clear()
    }

    internal fun putRawForTest(
        context: Context,
        id: String,
        kind: String,
        payload: String,
        createdAtMs: Long = System.currentTimeMillis(),
    ) {
        IngressStore.get(context).putRaw(id, kind, payload, createdAtMs)
    }

    internal fun attemptCountForTest(
        context: Context,
        id: String,
    ): Int? = IngressStore.get(context).attemptCount(id)
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
        if (!OneSignal.initWithContext(applicationContext)) {
            return if (runAttemptCount + 1 >= MAX_INIT_ATTEMPTS) Result.failure() else Result.retry()
        }

        var retryNeeded = false
        for (record in store.list()) {
            retryNeeded = processRecord(store, record) || retryNeeded
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun processRecord(
        store: IngressStore,
        record: IngressRecord,
    ): Boolean =
        when {
            record.kind == null -> {
                Logging.warn("Dropping notification ingress ${record.id} with unknown kind")
                store.delete(record.id)
                false
            }
            isExpired(record) -> {
                Logging.warn("Dropping expired notification ingress ${record.id}")
                store.delete(record.id)
                false
            }
            else ->
                try {
                    when (record.kind) {
                        IngressKind.FCM -> processFcm(record)
                        IngressKind.DISMISS -> processDismiss(record)
                    }
                    store.delete(record.id)
                    false
                } catch (e: Exception) {
                    handleRecordFailure(store, record, e)
                }
        }

    private fun handleRecordFailure(
        store: IngressStore,
        record: IngressRecord,
        error: Exception,
    ): Boolean {
        val nextAttempt = record.attemptCount + 1
        return if (nextAttempt >= MAX_RECORD_ATTEMPTS) {
            Logging.error("Dropping notification ingress ${record.id} after $nextAttempt attempts", error)
            store.delete(record.id)
            false
        } else {
            Logging.warn("Notification ingress ${record.id} failed attempt $nextAttempt", error)
            store.setAttemptCount(record.id, nextAttempt)
            true
        }
    }

    private fun isExpired(record: IngressRecord): Boolean =
        System.currentTimeMillis() - record.createdAtMs >= MAX_RECORD_AGE_MS

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

    companion object {
        internal const val MAX_RECORD_ATTEMPTS = 3
        internal const val MAX_INIT_ATTEMPTS = 3
        internal const val MAX_RECORD_AGE_MS = 24 * 60 * 60 * 1_000L
    }
}

private enum class IngressKind {
    FCM,
    DISMISS,
}

private data class IngressRecord(
    val id: String,
    val kind: IngressKind?,
    val action: String?,
    val payload: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0,
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
                created_at INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < SCHEMA_WITH_ATTEMPTS_VERSION) {
            database.execSQL("ALTER TABLE $TABLE ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun put(record: IngressRecord) {
        val values =
            ContentValues().apply {
                put("id", record.id)
                put("kind", requireNotNull(record.kind).name)
                put("action", record.action)
                put("payload", record.payload)
                put("created_at", record.createdAtMs)
                put(ATTEMPT_COUNT_COLUMN, record.attemptCount)
            }
        val inserted = writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) {
            values.remove("created_at")
            values.remove(ATTEMPT_COUNT_COLUMN)
            check(writableDatabase.update(TABLE, values, "id = ?", arrayOf(record.id)) == 1) {
                "Unable to update notification ingress"
            }
        }
    }

    fun list(): List<IngressRecord> {
        val records = mutableListOf<IngressRecord>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "kind", "action", "payload", "created_at", ATTEMPT_COUNT_COLUMN),
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
                        kind = enumValues<IngressKind>().firstOrNull { it.name == cursor.getString(KIND_COLUMN_INDEX) },
                        action = cursor.getString(ACTION_COLUMN_INDEX),
                        payload = cursor.getString(PAYLOAD_COLUMN_INDEX),
                        createdAtMs = cursor.getLong(CREATED_AT_COLUMN_INDEX),
                        attemptCount = cursor.getInt(ATTEMPT_COUNT_COLUMN_INDEX),
                    )
            }
        }
        return records
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
    }

    fun setAttemptCount(
        id: String,
        attemptCount: Int,
    ) {
        val values = ContentValues().apply { put(ATTEMPT_COUNT_COLUMN, attemptCount) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
    }

    fun attemptCount(id: String): Int? =
        readableDatabase.query(
            TABLE,
            arrayOf(ATTEMPT_COUNT_COLUMN),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    fun putRaw(
        id: String,
        kind: String,
        payload: String,
        createdAtMs: Long,
    ) {
        val values =
            ContentValues().apply {
                put("id", id)
                put("kind", kind)
                put("payload", payload)
                put("created_at", createdAtMs)
                put(ATTEMPT_COUNT_COLUMN, 0)
            }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
        private const val SCHEMA_WITH_ATTEMPTS_VERSION = 2
        private const val DATABASE_VERSION = SCHEMA_WITH_ATTEMPTS_VERSION
        private const val TABLE = "notification_ingress"
        private const val ATTEMPT_COUNT_COLUMN = "attempt_count"
        private const val ID_COLUMN_INDEX = 0
        private const val KIND_COLUMN_INDEX = 1
        private const val ACTION_COLUMN_INDEX = 2
        private const val PAYLOAD_COLUMN_INDEX = 3
        private const val CREATED_AT_COLUMN_INDEX = 4
        private const val ATTEMPT_COUNT_COLUMN_INDEX = 5

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
