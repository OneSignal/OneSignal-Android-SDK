package com.onesignal.debug.internal.logging.logger.android

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
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

    beforeEach {
        dir = Files.createTempDirectory("crashes").toFile()
    }

    afterEach {
        dir.deleteRecursively()
    }

    fun write(name: String, ageMsAgo: Long = 60_000L): File =
        File(dir, name).apply {
            writeBytes("x".toByteArray())
            setLastModified(System.currentTimeMillis() - ageMsAgo)
        }

    test("deleteUnrecognizedEntries removes stale legacy files and keeps owned .otlp records") {
        write("1784621689841") // legacy otel bare-millis file
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
})
