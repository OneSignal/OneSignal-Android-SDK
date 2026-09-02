package com.onesignal.core.internal.device

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.device.impl.AndroidFidEnvReader
import com.onesignal.core.internal.device.impl.FidEnvService
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class FidEnvReaderTests : FunSpec({
    lateinit var context: Context

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
    }

    afterEach {
        unmockkObject(AndroidUtils)
    }

    test("default test app has no google-services resources and no FID flag") {
        val snapshot = AndroidFidEnvReader(context).collect(null)

        snapshot.googleServices shouldBe false
        snapshot.fidFlag shouldBe false
        snapshot.defaultFirebaseApp shouldBe false
        snapshot.firebaseInitProvider shouldBe false
        snapshot.senderMatch shouldBe null
        snapshot.minSdk shouldBe context.applicationInfo.minSdkVersion
        snapshot.targetSdk shouldBe context.applicationInfo.targetSdkVersion
        snapshot.agpVersion shouldBe null
    }

    test("gs and sender match follow google-services string resources") {
        mockkObject(AndroidUtils)
        every { AndroidUtils.getResourceString(context, "google_app_id", null) } returns "1:388536902528:android:abc"
        every { AndroidUtils.getResourceString(context, "gcm_defaultSenderId", null) } returns "388536902528"
        every { AndroidUtils.getManifestMetaBoolean(context, any()) } returns false

        val snapshot = AndroidFidEnvReader(context).collect("388536902528")

        snapshot.googleServices shouldBe true
        snapshot.senderMatch shouldBe true
    }

    test("sender mismatch is reported when the dashboard sender differs") {
        mockkObject(AndroidUtils)
        every { AndroidUtils.getResourceString(context, "google_app_id", null) } returns "1:1:android:abc"
        every { AndroidUtils.getResourceString(context, "gcm_defaultSenderId", null) } returns "111"
        every { AndroidUtils.getManifestMetaBoolean(context, any()) } returns false

        val snapshot = AndroidFidEnvReader(context).collect("999")

        snapshot.googleServices shouldBe true
        snapshot.senderMatch shouldBe false
    }

    test("FidEnvService returns a header and does not throw") {
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val configStore = MockHelper.configModelStore()
        val header = FidEnvService(applicationService, configStore).headerValue()

        header.shouldContain("gs=0")
        header.shouldContain("agp=-")
        header.shouldContain("snd=-")
    }

    test("snd stays unknown until dashboard params for this appId have hydrated") {
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val configStore =
            MockHelper.configModelStore {
                it.googleProjectNumber = "123"
                it.isInitializedWithRemote = false
            }
        val service = FidEnvService(applicationService, configStore)

        service.headerValue().shouldContain("snd=-")

        configStore.model.isInitializedWithRemote = true

        service.headerValue().shouldContain("snd=0")
    }
})
