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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend

private fun dummy(type: KType): Any? {
    if (type.isMarkedNullable) return null
    val kClass = type.classifier as KClass<*>
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

// Calls every member of [type] on [target()] with dummy args; returns the ones that threw or hung.
private fun sweep(
    type: KClass<*>,
    target: () -> Any,
): List<String> =
    type.members
        .filter { it.name !in setOf("equals", "hashCode", "toString") && !it.startsInit() }
        .mapNotNull { member ->
            try {
                runBlocking {
                    withTimeout(5_000) {
                        when (member) {
                            is KProperty<*> -> {
                                member.getter.call(target())
                                if (member is KMutableProperty<*>) member.setter.call(target(), dummy(member.returnType))
                            }
                            is KFunction<*> -> member.callSuspend(target(), *member.parameters.drop(1).map { dummy(it.type) }.toTypedArray())
                        }
                        Unit
                    }
                }
                null
            } catch (e: TimeoutCancellationException) {
                "${type.simpleName}.${member.label()} hung for 5s"
            } catch (e: Throwable) {
                "${type.simpleName}.${member.label()} threw ${(e as? InvocationTargetException)?.targetException ?: e}"
            }
        }

// DebugManager's init resets the level to WARN, and WARN reaches the unmocked android.util.Log.
private fun quietOneSignal() = OneSignalImpV2().also { Logging.logLevel = LogLevel.NONE }

class OneSignalImpV2ApiSweepTests : FunSpec({
    test("no IOneSignal member throws or hangs before init") {
        sweep(IOneSignal::class) { quietOneSignal() } shouldBe emptyList()
    }

    test("no member of a manager handed out before init throws or hangs") {
        val os = quietOneSignal()
        listOf<Pair<KClass<*>, () -> Any>>(
            IUserManager::class to { os.user },
            IPushSubscription::class to { os.user.pushSubscription },
            INotificationsManager::class to { os.notifications },
            IInAppMessagesManager::class to { os.inAppMessages },
            ILocationManager::class to { os.location },
            ISessionManager::class to { os.session },
        ).flatMap { (type, target) -> sweep(type, target) } shouldBe emptyList()
    }
})
