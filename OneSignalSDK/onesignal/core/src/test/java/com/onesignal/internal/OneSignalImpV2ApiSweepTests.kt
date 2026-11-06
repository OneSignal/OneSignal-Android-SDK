package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.OneSignalUserProfile
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend

private fun dummy(type: KType): Any? {
    val kClass = type.classifier as KClass<*>
    // A nullable string still has to reach the gate. A nullable Context would start a real init.
    if (type.isMarkedNullable && kClass != String::class) return null
    return when {
        kClass == String::class -> "sample"
        kClass == Boolean::class -> false
        kClass == Int::class -> 0
        kClass == Float::class -> 0f
        kClass == Map::class -> emptyMap<String, String>()
        kClass == Collection::class -> emptyList<String>()
        kClass == OneSignalUserProfile::class -> OneSignalUserProfile.builder().build()
        kClass.java.isEnum -> kClass.java.enumConstants!!.first()
        else -> mockkClass(kClass, relaxed = true)
    }
}

private fun KCallable<*>.label() = "$name(${parameters.drop(1).joinToString { it.type.toString() }})"

// Initializing with a real context is SDKInitV2Tests' job; here only the null-context variants run.
private fun KCallable<*>.startsInit() = parameters.any { it.type.classifier == Context::class && !it.type.isMarkedNullable }

private fun KCallable<*>.isApi() = name !in setOf("equals", "hashCode", "toString") && !startsInit()

private val sweepThread = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }

// A blocking wait inside the member cannot be interrupted by withTimeout, so the deadline is enforced from outside.
private fun callWithDeadline(block: () -> Unit) {
    val future = sweepThread.submit(block)
    try {
        future.get(5, TimeUnit.SECONDS)
    } catch (e: java.util.concurrent.ExecutionException) {
        throw e.cause ?: e
    }
}

private fun invoke(
    member: KCallable<*>,
    target: Any,
    args: (KParameter) -> Any? = { dummy(it.type) },
) {
    runBlocking {
        when (member) {
            is KProperty<*> -> {
                member.getter.call(target)
                if (member is KMutableProperty<*>) member.setter.call(target, dummy(member.returnType))
            }
            is KFunction<*> -> member.callSuspend(target, *member.parameters.drop(1).map(args).toTypedArray())
            else -> Unit
        }
        Unit
    }
}

// Calls every member of [type] on [target()] with dummy args; returns the ones that threw or hung.
private fun sweep(
    type: KClass<*>,
    target: () -> Any,
): List<String> =
    type.members.filter { it.isApi() }.mapNotNull { member ->
        try {
            callWithDeadline { invoke(member, target()) }
            null
        } catch (e: TimeoutException) {
            "${type.simpleName}.${member.label()} hung for 5s"
        } catch (e: Throwable) {
            "${type.simpleName}.${member.label()} threw ${(e as? InvocationTargetException)?.targetException ?: e}"
        }
    }

private fun KParameter.isRequiredReference() = !type.isMarkedNullable && !(type.classifier as KClass<*>).java.isPrimitive && type.classifier != Boolean::class && type.classifier != Int::class && type.classifier != Float::class

// Passes null for each required parameter in turn; returns the calls that hung or threw anything but a NullPointerException.
private fun nullSweep(
    type: KClass<*>,
    target: () -> Any,
): List<String> =
    type.members.filterIsInstance<KFunction<*>>().filter { it.isApi() }.flatMap { member ->
        member.parameters.drop(1).filter { it.isRequiredReference() }.mapNotNull { nulled ->
            val outcome =
                try {
                    callWithDeadline { invoke(member, target()) { if (it == nulled) null else dummy(it.type) } }
                    null
                } catch (e: TimeoutException) {
                    "hung for 5s"
                } catch (e: Throwable) {
                    val cause = (e as? InvocationTargetException)?.targetException ?: e
                    if (cause is NullPointerException) null else "threw $cause"
                }
            outcome?.let { "${type.simpleName}.${member.label()} with null ${nulled.name} $it" }
        }
    }

// DebugManager's init resets the level to WARN, and WARN reaches the unmocked android.util.Log.
private fun quietOneSignal() = OneSignalImpV2().also { Logging.logLevel = LogLevel.NONE }

private fun failedOneSignal() =
    quietOneSignal().also {
        it.gate.tryStart()
        it.gate.complete(InitState.FAILED)
    }

private fun managers(os: OneSignalImpV2): List<Pair<KClass<*>, () -> Any>> =
    listOf(
        IUserManager::class to { os.user },
        IPushSubscription::class to { os.user.pushSubscription },
        INotificationsManager::class to { os.notifications },
        IInAppMessagesManager::class to { os.inAppMessages },
        ILocationManager::class to { os.location },
        ISessionManager::class to { os.session },
    )

class OneSignalImpV2ApiSweepTests : FunSpec({
    test("no IOneSignal member throws or hangs before init") {
        sweep(IOneSignal::class) { quietOneSignal() } shouldBe emptyList()
    }

    test("no member of a manager handed out before init throws or hangs") {
        val os = quietOneSignal()
        managers(os).flatMap { (type, target) -> sweep(type, target) } shouldBe emptyList()
    }

    test("no IOneSignal member throws or hangs after a failed init") {
        sweep(IOneSignal::class) { failedOneSignal() } shouldBe emptyList()
    }

    test("no member of a manager handed out after a failed init throws or hangs") {
        val os = failedOneSignal()
        managers(os).flatMap { (type, target) -> sweep(type, target) } shouldBe emptyList()
    }

    // Java callers passing null for a still-required argument hit Kotlin's parameter check. externalId,
    // context, and appId are nullable and log instead. Either way the call must not hang.
    test("a null argument for a required parameter never hangs and throws nothing but a NullPointerException") {
        val before = quietOneSignal()
        val failed = failedOneSignal()
        (listOf(IOneSignal::class to { before }) + managers(before)).flatMap { (type, target) -> nullSweep(type, target) } shouldBe emptyList()
        (listOf(IOneSignal::class to { failed }) + managers(failed)).flatMap { (type, target) -> nullSweep(type, target) } shouldBe emptyList()
    }
})
