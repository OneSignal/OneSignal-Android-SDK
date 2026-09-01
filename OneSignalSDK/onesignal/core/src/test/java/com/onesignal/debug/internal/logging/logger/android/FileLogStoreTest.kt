package com.onesignal.debug.internal.logging.logger.android

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.logger.crash.CrashRetention
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class FileLogStoreTest : FunSpec({

    lateinit var dir: File

    // Assert against the shared policy, never against copies of its numbers.
    val policy = CrashRetention.defaultPolicy

    beforeEach {
        dir = Files.createTempDirectory("crashes").toFile()
    }

    afterEach {
        dir.deleteRecursively()
    }

    fun write(name: String, ageMsAgo: Long = 60_000L, sizeBytes: Int = 1): File =
        File(dir, name).apply {
            writeBytes(ByteArray(sizeBytes) { 'x'.code.toByte() })
            setLastModified(System.currentTimeMillis() - ageMsAgo)
        }

    /**
     * Stands in for a timestamp the platform cannot read. The assertion is load-bearing: not every
     * filesystem honours `setLastModified(0)`, and one that ignored it would still pass.
     */
    fun writeUnreadableMtime(name: String, sizeBytes: Int = 1): File =
        File(dir, name).apply {
            writeBytes(ByteArray(sizeBytes) { 'x'.code.toByte() })
            setLastModified(0)
            lastModified() shouldBe 0L
        }

    test("deleteUnrecognizedEntries removes stale legacy files and keeps owned .otlp records") {
        write("1784621689841") // bare-millis file left by a pre-upgrade otel session
        write("stale.tmp") // stray temp
        write("123-abc.otlp") // owned logger record
        write("456-def.otlp") // owned logger record

        val purged = runBlocking { FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 0) }

        purged shouldBe 2
        File(dir, "1784621689841").exists() shouldBe false
        File(dir, "stale.tmp").exists() shouldBe false
        File(dir, "123-abc.otlp").exists() shouldBe true
        File(dir, "456-def.otlp").exists() shouldBe true
    }

    test("deleteUnrecognizedEntries preserves too-young foreign files") {
        write("too-young-legacy", ageMsAgo = 100)
        write("stale-legacy", ageMsAgo = 10_000)

        val purged = runBlocking {
            FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 5_000)
        }

        purged shouldBe 1
        File(dir, "too-young-legacy").exists() shouldBe true
        File(dir, "stale-legacy").exists() shouldBe false
    }

    test("deleteUnrecognizedEntries is a no-op when only owned records exist") {
        write("123-abc.otlp")

        val purged = runBlocking { FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 0) }

        purged shouldBe 0
        File(dir, "123-abc.otlp").exists() shouldBe true
    }

    test("deleteUnrecognizedEntries returns 0 for a missing directory") {
        val missing = File(dir, "does-not-exist")

        val purged = runBlocking {
            FileLogStore(missing.path).deleteUnrecognizedEntries(minAgeMillis = 0)
        }

        purged shouldBe 0
    }

    test("deleteUnrecognizedEntries is idempotent across repeated calls") {
        write("legacy-file")
        write("789-ghi.otlp")

        val store = FileLogStore(dir.path)
        val first = runBlocking { store.deleteUnrecognizedEntries(minAgeMillis = 0) }
        val second = runBlocking { store.deleteUnrecognizedEntries(minAgeMillis = 0) }

        first shouldBe 1
        second shouldBe 0
        File(dir, "789-ghi.otlp").exists() shouldBe true
    }

    test("listReadable drops an owned record past the max read age and deletes it from disk") {
        write("expired-123.otlp", ageMsAgo = policy.maxReadAgeMillis + 60_000)
        write("fresh-456.otlp", ageMsAgo = 60_000)

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("fresh-456.otlp")
        File(dir, "expired-123.otlp").exists() shouldBe false
        File(dir, "fresh-456.otlp").exists() shouldBe true
    }

    test("listReadable returns and retains an owned record inside the age window") {
        write("edge-123.otlp", ageMsAgo = policy.maxReadAgeMillis - 60_000)

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("edge-123.otlp")
        File(dir, "edge-123.otlp").exists() shouldBe true
    }

    test("deleteUnrecognizedEntries reclaims expired owned records without counting them as foreign") {
        write("expired-123.otlp", ageMsAgo = policy.maxReadAgeMillis + 60_000)
        write("fresh-456.otlp", ageMsAgo = 60_000)
        write("1784621689841")

        val purged = runBlocking { FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 0) }

        purged shouldBe 1
        File(dir, "expired-123.otlp").exists() shouldBe false
        File(dir, "fresh-456.otlp").exists() shouldBe true
        File(dir, "1784621689841").exists() shouldBe false
    }

    test("save evicts oldest-first once the record count cap is exceeded") {
        // Distinct mtimes so "oldest" is unambiguous; the newest seeded record is 1s old.
        repeat(policy.maxRecordCount) { i ->
            write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1))
        }
        val oldest = "seed-${policy.maxRecordCount - 1}.otlp"

        FileLogStore(dir.path).save("new".toByteArray()) shouldBe true

        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
        File(dir, oldest).exists() shouldBe false
        File(dir, "seed-0.otlp").exists() shouldBe true
    }

    test("save evicts oldest-first once the total byte cap is exceeded") {
        // Each is just under the per-record cap, so only their combined size breaches the budget.
        val nearCap = (policy.maxRecordBytes - 12_288).toInt()
        repeat(5) { i -> write("big-$i.otlp", ageMsAgo = 10_000L * (i + 1), sizeBytes = nearCap) }

        FileLogStore(dir.path).save("new".toByteArray()) shouldBe true

        File(dir, "big-4.otlp").exists() shouldBe false
        File(dir, "big-0.otlp").exists() shouldBe true
    }

    test("save refuses a payload over the per-record limit and writes nothing") {
        val oversized = ByteArray((policy.maxRecordBytes + 1).toInt())

        FileLogStore(dir.path).save(oversized) shouldBe false

        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe 0
    }

    test("an inherited oversized record is still offered for upload, not deleted unread") {
        // Written by a build predating the write-time limit, so it is a real crash report.
        write("inherited.otlp", ageMsAgo = 60_000, sizeBytes = (policy.maxRecordBytes + 1).toInt())

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("inherited.otlp")
        File(dir, "inherited.otlp").exists() shouldBe true
    }

    // Both sort keys clamp to now, so a record written against a future-dated backlog ties with
    // it and filesystem order decides. Repeat: one attempt would pass most of the time anyway.
    test("save never evicts the record it just wrote, whatever order the backlog lists in") {
        repeat(25) {
            val trialDir = Files.createTempDirectory("crashes-keepname").toFile()
            try {
                repeat(policy.maxRecordCount + 3) { i ->
                    val ahead = System.currentTimeMillis() + 3_600_000L + i
                    File(trialDir, "$ahead-seed$i.otlp").apply {
                        writeBytes(ByteArray(1) { 'x'.code.toByte() })
                        setLastModified(System.currentTimeMillis() + 60_000L)
                    }
                }

                FileLogStore(trialDir.path).save("new".toByteArray()) shouldBe true

                val remaining = trialDir.listFiles()!!.filter { it.name.endsWith(policy.ownedSuffix) }
                remaining.count { it.readText() == "new" } shouldBe 1
                remaining.size shouldBe policy.maxRecordCount
            } finally {
                trialDir.deleteRecursively()
            }
        }
    }

    test("listReadable evicts an inherited over-cap backlog instead of returning it") {
        repeat(policy.maxRecordCount + 10) { i -> write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1)) }

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.size shouldBe policy.maxRecordCount
        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
    }

    test("an expired record that cannot be deleted is still withheld from readers") {
        write("expired-stuck.otlp", ageMsAgo = policy.maxReadAgeMillis + 60_000)
        write("fresh.otlp", ageMsAgo = 60_000)
        // Read-only dir makes unlink fail on POSIX without making the entries unreadable.
        dir.setWritable(false)

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        dir.setWritable(true)
        readable.map { it.id } shouldBe listOf("fresh.otlp")
        File(dir, "expired-stuck.otlp").exists() shouldBe true
    }

    test("an owned record with an unreadable timestamp is dated from its name and stays readable") {
        val writtenMs = System.currentTimeMillis() - 60_000
        writeUnreadableMtime("$writtenMs-abc.otlp")

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("$writtenMs-abc.otlp")
        File(dir, "$writtenMs-abc.otlp").exists() shouldBe true
    }

    // Dating from the name must not become a way to outlive the ceiling.
    test("an owned record with an unreadable timestamp still expires on the millis in its name") {
        val writtenMs = System.currentTimeMillis() - (policy.maxReadAgeMillis + 60_000)
        writeUnreadableMtime("$writtenMs-abc.otlp")

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe emptyList()
        File(dir, "$writtenMs-abc.otlp").exists() shouldBe false
    }

    // Neither clock is available: no mtime, no millis in the name. A failed read is not evidence
    // of age, so nothing may expire it — nor may it be read, since it may still be mid-write.
    test("an undatable owned record is never age-expired, only withheld from readers") {
        writeUnreadableMtime("undatable-abc.otlp")
        write("fresh-456.otlp", ageMsAgo = 60_000)

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("fresh-456.otlp")
        File(dir, "undatable-abc.otlp").exists() shouldBe true
        File(dir, "fresh-456.otlp").exists() shouldBe true
    }

    // Exempt from expiry is not exempt from the caps, or it would leak for the install's life.
    test("an undatable owned record still counts toward the caps and is evicted before datable ones") {
        repeat(policy.maxRecordCount) { i -> write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1)) }
        writeUnreadableMtime("undatable-abc.otlp")

        runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        File(dir, "undatable-abc.otlp").exists() shouldBe false
        File(dir, "seed-${policy.maxRecordCount - 1}.otlp").exists() shouldBe true
        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
    }

    // It may be another writer's file, mid-write, so it is left rather than reaped on a guess.
    test("a foreign file with an unreadable timestamp is not purged as stale") {
        writeUnreadableMtime("stale.tmp")

        val purged = runBlocking {
            FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 5_000)
        }

        purged shouldBe 0
        File(dir, "stale.tmp").exists() shouldBe true
    }

    // What `save` leaves when the process dies between write and rename. This purge is the only
    // pass that reclaims one, and it needs an age, so an undatable temp is stranded forever.
    test("an interrupted write is reclaimed even when its timestamp is unreadable") {
        val writtenMs = System.currentTimeMillis() - 600_000
        writeUnreadableMtime("$writtenMs-abc${policy.ownedTempSuffix}")
        // Another writer's scheme: dating it from its `3` would reap a seconds-old file mid-write.
        writeUnreadableMtime("3-tmp.dat")

        val purged = runBlocking {
            FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 5_000)
        }

        purged shouldBe 1
        File(dir, "$writtenMs-abc${policy.ownedTempSuffix}").exists() shouldBe false
        File(dir, "3-tmp.dat").exists() shouldBe true
    }

    test("an interrupted write younger than the age gate is left alone") {
        val writtenMs = System.currentTimeMillis() - 100
        writeUnreadableMtime("$writtenMs-abc${policy.ownedTempSuffix}")

        val purged = runBlocking {
            FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 5_000)
        }

        purged shouldBe 0
        File(dir, "$writtenMs-abc${policy.ownedTempSuffix}").exists() shouldBe true
    }

    test("deleteUnrecognizedEntries evicts an inherited over-cap backlog") {
        repeat(policy.maxRecordCount + 10) { i -> write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1)) }
        write("1784621689841")

        val purged = runBlocking { FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 0) }

        // Owned evictions are not counted as foreign purges.
        purged shouldBe 1
        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
    }
})
