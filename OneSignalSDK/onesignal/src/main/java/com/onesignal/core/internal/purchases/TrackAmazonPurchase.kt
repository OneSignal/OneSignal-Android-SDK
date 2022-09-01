/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.core.internal.purchases

import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.RequestId
import com.amazon.device.iap.model.UserDataResponse
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.suspendifyOnMain
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.PurchaseInfo
import com.onesignal.core.internal.operations.TrackPurchaseOperation
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.user.IUserSwitcher
import java.lang.ClassCastException
import java.lang.Exception
import java.lang.NullPointerException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.util.HashSet

internal class TrackAmazonPurchase(
    private val _applicationService: IApplicationService,
    private val _operationRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
    private val _userSwitcher: IUserSwitcher
) : IStartableService, IApplicationLifecycleHandler {
    private var canTrack = false
    private var osPurchasingListener: OSPurchasingListener? = null
    private var listenerHandlerObject: Any? = null
    private var listenerHandlerField: Field? = null

    override fun start() {
        if (!canTrack())
            return

        try {
            // 2.0.1
            val listenerHandlerClass = Class.forName("com.amazon.device.iap.internal.d")
            listenerHandlerObject = try {
                // iap v2.x
                listenerHandlerClass.getMethod("d").invoke(null)
            } catch (e: NullPointerException) {
                // appstore v3.x
                listenerHandlerClass.getMethod("e").invoke(null)
            }
            val locListenerHandlerField = listenerHandlerClass.getDeclaredField("f")
            locListenerHandlerField.isAccessible = true
            osPurchasingListener = OSPurchasingListener(_operationRepo, _configModelStore, _userSwitcher)
            osPurchasingListener!!.orgPurchasingListener = locListenerHandlerField.get(listenerHandlerObject) as PurchasingListener

            listenerHandlerField = locListenerHandlerField
            canTrack = true
            setListener()
            // Can replace all catches with ReflectiveOperationException win min API is 19
        } catch (e: ClassNotFoundException) {
            logAmazonIAPListenerError(e)
        } catch (e: IllegalAccessException) {
            logAmazonIAPListenerError(e)
        } catch (e: InvocationTargetException) {
            logAmazonIAPListenerError(e)
        } catch (e: NoSuchMethodException) {
            logAmazonIAPListenerError(e)
        } catch (e: NoSuchFieldException) {
            logAmazonIAPListenerError(e)
        } catch (e: ClassCastException) {
            logAmazonIAPListenerError(e)
        }

        _applicationService.addApplicationLifecycleHandler(this)
    }

    private fun logAmazonIAPListenerError(e: Exception) {
        Logging.error("Error adding Amazon IAP listener.", e)
        e.printStackTrace()
    }

    override fun onFocus() { }

    override fun onUnfocused() {
        if (!canTrack) return
        try {
            val curPurchasingListener =
                listenerHandlerField!![listenerHandlerObject] as PurchasingListener
            if (curPurchasingListener !== osPurchasingListener) {
                osPurchasingListener!!.orgPurchasingListener = curPurchasingListener
                setListener()
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }

    private fun setListener() {
        PurchasingService.registerListener(_applicationService.appContext, osPurchasingListener)
    }

    private inner class OSPurchasingListener(
        private val _operationRepo: IOperationRepo,
        private val _configModelStore: ConfigModelStore,
        private val _userSwitcher: IUserSwitcher
    ) : PurchasingListener {
        var orgPurchasingListener: PurchasingListener? = null
        private var lastRequestId: RequestId? = null
        private var currentMarket: String? = null
        private fun marketToCurrencyCode(market: String?): String {
            when (market) {
                "US" -> return "USD"
                "GB" -> return "GBP"
                "DE", "FR", "ES", "IT" -> return "EUR"
                "JP" -> return "JPY"
                "CA" -> return "CDN"
                "BR" -> return "BRL"
                "AU" -> return "AUD"
            }
            return ""
        }

        override fun onProductDataResponse(response: ProductDataResponse) {
            if (lastRequestId != null && lastRequestId.toString() == response.requestId.toString()) {
                when (response.requestStatus) {
                    ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                        val purchasesToReport = mutableListOf<PurchaseInfo>()
                        val products = response.productData
                        for (key in products.keys) {
                            val product = products[key]
                            val sku = product!!.sku
                            val iso = marketToCurrencyCode(currentMarket)
                            var priceStr = product.price

                            if (!priceStr.matches("^[0-9]".toRegex()))
                                priceStr = priceStr.substring(1)

                            val price = BigDecimal(priceStr)

                            purchasesToReport.add(PurchaseInfo(sku, iso, price))
                        }

                        _operationRepo.enqueue(TrackPurchaseOperation(_configModelStore.get().appId!!, _userSwitcher.identityModel.oneSignalId.toString(), false, purchasesToReport))
                    }
                }
            } else if (orgPurchasingListener != null) orgPurchasingListener!!.onProductDataResponse(
                response
            )
        }

        override fun onPurchaseResponse(response: PurchaseResponse) {
            val status = response.requestStatus
            if (status == PurchaseResponse.RequestStatus.SUCCESSFUL) {
                currentMarket = response.userData.marketplace
                val productSkus: MutableSet<String> = HashSet()
                productSkus.add(response.receipt.sku)
                lastRequestId = PurchasingService.getProductData(productSkus)
            }
            if (orgPurchasingListener != null) orgPurchasingListener!!.onPurchaseResponse(response)
        }

        override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
            if (orgPurchasingListener != null) orgPurchasingListener!!.onPurchaseUpdatesResponse(
                response
            )
        }

        override fun onUserDataResponse(response: UserDataResponse) {
            if (orgPurchasingListener != null) orgPurchasingListener!!.onUserDataResponse(response)
        }
    }

    companion object {
        fun canTrack(): Boolean {
            try {
                Class.forName("com.amazon.device.iap.PurchasingListener")
                return true
            } catch (e: ClassNotFoundException) {
            }
            return false
        }
    }
}
