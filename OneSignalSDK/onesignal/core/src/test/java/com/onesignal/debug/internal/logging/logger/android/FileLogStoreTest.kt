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

    // The bounds FileLogStore enforces; asserting against the shared policy rather than
    // copies of its numbers keeps these expectations tied to what the store actually uses.
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
        // Each is just under the per-record cap, so only their combined size can breach the
        // total budget — five of them do, and the oldest is the one that loses.
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
        // Written by a build predating the write-time limit. Deleting it before an upload
        // attempt would silently destroy a real crash report.
        write("inherited.otlp", ageMsAgo = 60_000, sizeBytes = (policy.maxRecordBytes + 1).toInt())

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.map { it.id } shouldBe listOf("inherited.otlp")
        File(dir, "inherited.otlp").exists() shouldBe true
    }

    // Both sort keys clamp to now, so a record written while the backlog is dated ahead of the
    // clock lands in a tie group with it, and the order inside that group is whatever the
    // filesystem lists. Only the explicit keepName reservation guarantees the new record
    // survives; without it eviction is a coin flip, so one attempt would pass most of the time.
    // Repeating drives the odds of a false pass to nil.
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

    // The uploader paths must reclaim a backlog inherited from a build without caps —
    // otherwise it is only trimmed the next time a crash happens to be written.

    test("listReadable evicts an inherited over-cap backlog instead of returning it") {
        repeat(policy.maxRecordCount + 10) { i -> write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1)) }

        val readable = runBlocking { FileLogStore(dir.path).listReadable(minAgeMillis = 0) }

        readable.size shouldBe policy.maxRecordCount
        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
    }

    // A delete can fail (read-only dir, filesystem error). The record must stay unreadable
    // regardless, and must not resurface on a later pass just because it survived.
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

    test("deleteUnrecognizedEntries evicts an inherited over-cap backlog") {
        repeat(policy.maxRecordCount + 10) { i -> write("seed-$i.otlp", ageMsAgo = 1_000L * (i + 1)) }
        write("1784621689841")

        val purged = runBlocking { FileLogStore(dir.path).deleteUnrecognizedEntries(minAgeMillis = 0) }

        // Owned evictions are not counted as foreign purges.
        purged shouldBe 1
        dir.listFiles()!!.count { it.name.endsWith(policy.ownedSuffix) } shouldBe policy.maxRecordCount
    }
})
