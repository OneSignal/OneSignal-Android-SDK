package com.onesignal.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class PIIHasherTests : FunSpec({

    test("hash produces 64-char lowercase hex string") {
        val result = PIIHasher.hash("test@example.com")
        result shouldHaveLength 64
        result shouldMatch Regex("^[a-f0-9]{64}$")
    }

    test("hash is deterministic") {
        PIIHasher.hash("test@example.com") shouldBe PIIHasher.hash("test@example.com")
    }

    test("hash produces different output for different input") {
        val hash1 = PIIHasher.hash("user1@example.com")
        val hash2 = PIIHasher.hash("user2@example.com")
        (hash1 != hash2) shouldBe true
    }

    test("hash matches known SHA-256 digest") {
        // SHA-256 of "hello" is well-known
        PIIHasher.hash("hello") shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }

    test("isHashed returns true for valid 64-char hex string") {
        val hashed = PIIHasher.hash("test@example.com")
        PIIHasher.isHashed(hashed) shouldBe true
    }

    test("isHashed returns false for plain email") {
        PIIHasher.isHashed("test@example.com") shouldBe false
    }

    test("isHashed returns false for phone number") {
        PIIHasher.isHashed("+15558675309") shouldBe false
    }

    test("isHashed returns false for empty string") {
        PIIHasher.isHashed("") shouldBe false
    }

    test("isHashed returns false for uppercase hex") {
        val upper = PIIHasher.hash("test").uppercase()
        PIIHasher.isHashed(upper) shouldBe false
    }

    test("isHashed returns false for 63-char hex string") {
        PIIHasher.isHashed("a".repeat(63)) shouldBe false
    }

    test("isHashed returns false for 65-char hex string") {
        PIIHasher.isHashed("a".repeat(65)) shouldBe false
    }
})
