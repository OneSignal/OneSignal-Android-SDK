package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.IHostPermissionPrompt
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

@RobolectricTest
class RequestPermissionServiceTests : FunSpec({
    beforeTest {
        Logging.logLevel = LogLevel.NONE
        OneSignalWrapper.hostPermissionPrompt = null
    }

    afterTest {
        OneSignalWrapper.hostPermissionPrompt = null
    }

    test("startPrompt launches PermissionsActivity when it is resolvable") {
        val env = Env()

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.hostActivity.startActivity(any()) }
        verify(exactly = 0) { env.callback.onReject(any()) }
        verify(exactly = 0) { env.app.removeActivityLifecycleHandler(any()) }
    }

    test("startPrompt rejects and unsubscribes when no host can prompt") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.hostActivity.startActivity(any()) }
        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 1) { env.app.removeActivityLifecycleHandler(env.handler) }
    }

    test("startPrompt removes the handler when PermissionsActivity is already current") {
        val env = Env()
        val permissionsActivity = mockk<PermissionsActivity>(relaxed = true)

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(permissionsActivity)

        verify(exactly = 0) { permissionsActivity.startActivity(any()) }
        verify(exactly = 0) { env.callback.onReject(any()) }
        verify(exactly = 1) { env.app.removeActivityLifecycleHandler(env.handler) }
    }

    test("startPrompt is a no-op while waiting") {
        val env = Env()
        env.service.waiting = true

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)

        verify(exactly = 0) { env.app.addActivityLifecycleHandler(any()) }
    }

    test("a granted wrapper host prompt accepts instead of crashing") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        val prompt = RecordingPrompt(granted = true)
        OneSignalWrapper.hostPermissionPrompt = prompt

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        prompt.requested shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onAccept() }
        verify(exactly = 0) { env.callback.onReject(any()) }
    }

    test("a denied wrapper host prompt reports the settings fallback decision") {
        val env = Env()
        env.markPreviouslyPrompted()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        val prompt = RecordingPrompt(granted = false)
        OneSignalWrapper.hostPermissionPrompt = prompt

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        prompt.requested shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("a wrapper host prompt with no activity completes the prompt as denied") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        OneSignalWrapper.hostPermissionPrompt = DecliningPrompt()

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("a wrapper host prompt that throws completes the prompt as denied") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        OneSignalWrapper.hostPermissionPrompt = ThrowingPrompt()

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.callback.onReject(true) }
    }

    test("a successful wrapper host prompt does not also prompt on the registry") {
        val env = Env()
        val registry = TestRegistry(result = true)
        val activity = env.componentActivity(registry)
        OneSignalWrapper.hostPermissionPrompt = RecordingPrompt(granted = true)

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(activity)

        registry.launched.shouldBeEmpty()
        verify(exactly = 1) { env.callback.onAccept() }
    }

    test("a ComponentActivity host prompts through its own result registry") {
        val env = Env()
        val registry = TestRegistry(result = true)
        val activity = env.componentActivity(registry)

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(activity)

        registry.lastInput shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onAccept() }
        verify(exactly = 0) { env.callback.onReject(any()) }
    }

    test("a denied ComponentActivity host prompt reports the settings fallback decision") {
        val env = Env()
        env.markPreviouslyPrompted()
        val registry = TestRegistry(result = false)
        val activity = env.componentActivity(registry)

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(activity)

        registry.lastInput shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("a result that arrives after the host activity is recreated still reaches the caller") {
        val env = Env()
        val registry = TestRegistry(result = null)
        val activity = env.componentActivity(registry)

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(activity)

        // The host is recreated: androidx restores the key but not our callback, and the OS
        // delivers the answer into the fresh registry before we get a chance to rebind.
        val savedState = Bundle()
        registry.onSaveInstanceState(savedState)
        val recreatedRegistry = TestRegistry(result = null)
        recreatedRegistry.onRestoreInstanceState(savedState)
        recreatedRegistry.dispatchResult(registry.launched.single(), true)

        env.handler.onActivityAvailable(env.componentActivity(recreatedRegistry))

        verify(exactly = 1) { env.callback.onAccept() }
        recreatedRegistry.launched.shouldBeEmpty()
    }

    test("a replaced host activity with no pending answer prompts again") {
        val env = Env()
        val registry = TestRegistry(result = null)

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.componentActivity(registry))

        val replacementRegistry = TestRegistry(result = true)
        env.handler.onActivityAvailable(env.componentActivity(replacementRegistry))

        replacementRegistry.lastInput shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onAccept() }
    }

    test("two host prompts in flight each keep their own settings fallback") {
        val env = Env()
        env.markPreviouslyPrompted()
        val locationCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)
        env.service.registerAsCallback(LOCATION_PERMISSION_TYPE, locationCallback)

        val registry = TestRegistry(result = null)
        val activity = env.componentActivity(registry)

        // Only the notification prompt asks for the settings fallback.
        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handlers[0].onActivityAvailable(activity)
        env.service.startPrompt(false, LOCATION_PERMISSION_TYPE, LOCATION_PERMISSION, Env.Callback::class.java)
        env.handlers[1].onActivityAvailable(activity)

        registry.launched.distinct().size shouldBe 2
        registry.launched.forEach { registry.dispatchResult(it, false) }

        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 1) { locationCallback.onReject(false) }
    }

    test("a second prompt for the same permission completes as denied rather than hanging") {
        val env = Env()
        val registry = TestRegistry(result = null)
        val activity = env.componentActivity(registry)

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handlers[0].onActivityAvailable(activity)
        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handlers[1].onActivityAvailable(activity)

        registry.launched.size shouldBe 1
        verify(exactly = 1) { env.callback.onReject(false) }

        registry.dispatchResult(registry.launched.single(), true)
        verify(exactly = 1) { env.callback.onAccept() }
    }
})

private const val PERMISSION_TYPE = "NOTIFICATION"
private const val ANDROID_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val LOCATION_PERMISSION_TYPE = "LOCATION"
private const val LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
private const val PROMPTED_PREFIX = "PROMPTED_PERMISSION_"

private fun List<Int>.shouldBeEmpty() = isEmpty() shouldBe true

/** Dispatches inline when [result] is set, otherwise holds the request so the test can drive it. */
private class TestRegistry(private val result: Boolean?) : ActivityResultRegistry() {
    val launched = mutableListOf<Int>()
    var lastInput: Any? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launched.add(requestCode)
        lastInput = input

        @Suppress("UNCHECKED_CAST")
        result?.let { dispatchResult(requestCode, it as O) }
    }
}

private class RecordingPrompt(private val granted: Boolean) : IHostPermissionPrompt {
    var requested: String? = null

    override fun request(
        androidPermission: String,
        callback: IHostPermissionPrompt.Callback,
    ): Boolean {
        requested = androidPermission
        callback.onResult(granted)
        return true
    }
}

private class DecliningPrompt : IHostPermissionPrompt {
    override fun request(
        androidPermission: String,
        callback: IHostPermissionPrompt.Callback,
    ): Boolean = false
}

private class ThrowingPrompt : IHostPermissionPrompt {
    override fun request(
        androidPermission: String,
        callback: IHostPermissionPrompt.Callback,
    ): Boolean = throw IllegalStateException("boom")
}

private class Env {
    interface Callback : IRequestPermissionService.PermissionCallback

    val callback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)
    val app = mockk<IApplicationService>(relaxed = true)
    val preferences = mockk<IPreferencesService>(relaxed = true)
    val hostActivity = mockk<Activity>(relaxed = true)
    val handlers = mutableListOf<IActivityLifecycleHandler>()
    val service = RequestPermissionService(app, preferences)

    val handler: IActivityLifecycleHandler
        get() = handlers.last()

    init {
        every { app.addActivityLifecycleHandler(capture(handlers)) } just runs
        service.registerAsCallback(PERMISSION_TYPE, callback)
    }

    /** A ComponentActivity host whose PermissionsActivity launch fails, as a stripped manifest does. */
    fun componentActivity(registry: ActivityResultRegistry): ComponentActivity {
        val activity = mockk<ComponentActivity>(relaxed = true)
        every { activity.startActivity(any()) } throws ActivityNotFoundException("missing")
        every { activity.activityResultRegistry } returns registry
        return activity
    }

    /**
     * Makes the settings fallback reachable: a denial only offers settings once OneSignal has
     * prompted for that permission before.
     */
    fun markPreviouslyPrompted() {
        every { preferences.getBool(any(), match { it.startsWith(PROMPTED_PREFIX) }, any()) } returns true
    }
}
