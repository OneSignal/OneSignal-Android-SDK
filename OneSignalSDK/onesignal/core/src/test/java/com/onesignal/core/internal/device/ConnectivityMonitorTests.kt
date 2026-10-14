package com.onesignal.core.internal.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.impl.ConnectivityMonitor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.robolectric.annotation.Config

private class MonitorMocks(initiallyOnline: Boolean) {
    val callback = slot<ConnectivityManager.NetworkCallback>()

    @Suppress("DEPRECATION")
    val connectivityManager =
        mockk<ConnectivityManager> {
            every { registerNetworkCallback(any<NetworkRequest>(), capture(callback)) } just runs
            every { activeNetworkInfo } returns
                if (initiallyOnline) mockk<NetworkInfo> { every { isConnected } returns true } else null
        }
    val applicationService =
        mockk<IApplicationService> {
            every { appContext } returns
                mockk<Context> {
                    every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
                }
        }
    var onlineCount = 0

    fun start() = ConnectivityMonitor(applicationService).addOnlineHandler { onlineCount++ }
}

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class ConnectivityMonitorTests : FunSpec({
    test("networks already up at registration do not count as coming online") {
        val mocks = MonitorMocks(initiallyOnline = true)
        mocks.start()

        mocks.callback.captured.onAvailable(mockk<Network>())

        mocks.onlineCount shouldBe 0
    }

    test("a network appearing while offline counts as coming online") {
        val mocks = MonitorMocks(initiallyOnline = false)
        mocks.start()

        mocks.callback.captured.onAvailable(mockk<Network>())

        mocks.onlineCount shouldBe 1
    }

    test("switching networks without going offline does not count as coming online") {
        val mocks = MonitorMocks(initiallyOnline = false)
        val wifi = mockk<Network>()
        val cellular = mockk<Network>()
        mocks.start()

        mocks.callback.captured.onAvailable(wifi)
        mocks.callback.captured.onAvailable(cellular)
        mocks.callback.captured.onLost(wifi)

        mocks.onlineCount shouldBe 1
    }

    test("losing every network and getting one back counts as coming online again") {
        val mocks = MonitorMocks(initiallyOnline = false)
        val wifi = mockk<Network>()
        mocks.start()

        mocks.callback.captured.onAvailable(wifi)
        mocks.callback.captured.onLost(wifi)
        mocks.callback.captured.onAvailable(wifi)

        mocks.onlineCount shouldBe 2
    }

    test("a failure registering the network callback does not throw") {
        val mocks = MonitorMocks(initiallyOnline = true)
        every { mocks.connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) } throws
            SecurityException()

        mocks.start()

        mocks.onlineCount shouldBe 0
    }
})
