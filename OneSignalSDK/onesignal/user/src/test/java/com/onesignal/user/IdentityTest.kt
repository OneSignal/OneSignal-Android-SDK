package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class IdentityTest : DescribeSpec({
    describe("Anonymous") {
        val identity = Identity.Anonymous()

        it("equals different Anonymous instance") {
            identity shouldBe Identity.Anonymous()
        }
    }

    describe("Known") {
        val mockId = "mockId"

        context("ExternalId") {
            val mockIdentity = { Identity.ExternalId(mockId) }
            val identity = mockIdentity()

            it("equals another instance, with same value") {
                identity shouldBe mockIdentity()
            }
            it("not equal with different value") {
                identity shouldNotBe Identity.ExternalId("2")
            }
        }

        context("ExternalIdWithAuthHash") {
            val mockAuthHash = "mockAuthHash"
            val mockIdentity = { id: String, authHash: String ->
                Identity.ExternalIdWithAuthHash(id, authHash)
            }
            val defaultMockIdentity = { mockIdentity(mockId, mockAuthHash) }
            val identity = defaultMockIdentity()

            it("equals another instance, with same values") {
                identity shouldBe defaultMockIdentity()
            }
            it("not equal with different id") {
                identity shouldNotBe mockIdentity("2", mockAuthHash)
            }
            it("not equal with different authHash") {
                identity shouldNotBe mockIdentity(mockId, "2")
            }
        }
    }
})
