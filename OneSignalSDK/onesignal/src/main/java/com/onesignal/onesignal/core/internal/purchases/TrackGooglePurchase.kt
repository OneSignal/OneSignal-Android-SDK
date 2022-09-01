/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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
package com.onesignal.onesignal.core.internal.purchases

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.PurchaseInfo
import com.onesignal.onesignal.core.internal.operations.TrackPurchaseOperation
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferencePlayerPurchasesKeys
import com.onesignal.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.internal.user.IUserSwitcher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.ArrayList

internal class TrackGooglePurchase(
    private val _applicationService: IApplicationService,
    private val _prefs: IPreferencesService,
    private val _operationRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
    private val _userSwitcher: IUserSwitcher
) : IStartableService, IApplicationLifecycleHandler {
    private var mServiceConn: ServiceConnection? = null
    private var mIInAppBillingService: Any? = null
    private var getPurchasesMethod: Method? = null
    private var getSkuDetailsMethod: Method? = null
    private val purchaseTokens: MutableList<String> = mutableListOf()

    // Any new purchases found count as pre-existing.
    // The constructor sets it to false if we already saved any purchases or already found out there isn't any.
    private var newAsExisting = true
    private var isWaitingForPurchasesRequest = false

    override fun start() {

        if (!canTrack(_applicationService.appContext))
            return

        try {
            val purchaseTokensString = _prefs.getString(PreferenceStores.PLAYER_PURCHASES, PreferencePlayerPurchasesKeys.PREFS_PURCHASE_TOKENS, "[]")
            val jsonPurchaseTokens = JSONArray(purchaseTokensString)

            for (i in 0 until jsonPurchaseTokens.length())
                purchaseTokens.add(jsonPurchaseTokens[i].toString())

            newAsExisting = jsonPurchaseTokens.length() == 0
            if (newAsExisting)
                newAsExisting = _prefs.getBool(PreferenceStores.PLAYER_PURCHASES, PreferencePlayerPurchasesKeys.PREFS_EXISTING_PURCHASES, true)!!
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        _applicationService.addApplicationLifecycleHandler(this)
        trackIAP()
    }

    override fun onFocus() {
        trackIAP()
    }

    override fun onUnfocused() {}

    private fun trackIAP() {
        if (mServiceConn == null) {
            val serviceConn = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName) {
                    iapEnabled = -99
                    mIInAppBillingService = null
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    try {
                        val stubClass =
                            Class.forName("com.android.vending.billing.IInAppBillingService\$Stub")
                        val asInterfaceMethod = getAsInterfaceMethod(stubClass)
                        asInterfaceMethod!!.isAccessible = true
                        mIInAppBillingService = asInterfaceMethod.invoke(null, service)
                        queryBoughtItems()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
            mServiceConn = serviceConn
            val serviceIntent = Intent("com.android.vending.billing.InAppBillingService.BIND")
            serviceIntent.setPackage("com.android.vending")
            _applicationService.appContext.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE)
        } else if (mIInAppBillingService != null)
            queryBoughtItems()
    }

    private fun queryBoughtItems() {
        if (isWaitingForPurchasesRequest) return
        Thread {
            isWaitingForPurchasesRequest = true
            try {
                if (getPurchasesMethod == null) {
                    getPurchasesMethod = getGetPurchasesMethod(IInAppBillingServiceClass)
                    getPurchasesMethod!!.isAccessible = true
                }
                val ownedItems = getPurchasesMethod!!.invoke(
                    mIInAppBillingService,
                    3,
                    _applicationService.appContext.packageName,
                    "inapp",
                    null
                ) as Bundle
                if (ownedItems.getInt("RESPONSE_CODE") == 0) {
                    val skusToAdd = ArrayList<String>()
                    val newPurchaseTokens = ArrayList<String>()
                    val ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST")
                    val purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST")
                    for (i in purchaseDataList!!.indices) {
                        val purchaseData = purchaseDataList[i]
                        val sku = ownedSkus!![i]
                        val itemPurchased = JSONObject(purchaseData)
                        val purchaseToken = itemPurchased.getString("purchaseToken")
                        if (!purchaseTokens.contains(purchaseToken) && !newPurchaseTokens.contains(
                                purchaseToken
                            )
                        ) {
                            newPurchaseTokens.add(purchaseToken)
                            skusToAdd.add(sku)
                        }
                    }
                    if (skusToAdd.size > 0) sendPurchases(
                        skusToAdd,
                        newPurchaseTokens
                    ) else if (purchaseDataList.size == 0) {
                        newAsExisting = false
                        _prefs.saveBool(PreferenceStores.PLAYER_PURCHASES, PreferencePlayerPurchasesKeys.PREFS_EXISTING_PURCHASES, false)
                    }

                    // TODO: Handle very large list. Test for continuationToken != null then call getPurchases again
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            isWaitingForPurchasesRequest = false
        }.start()
    }

    private fun sendPurchases(skusToAdd: ArrayList<String>, newPurchaseTokens: ArrayList<String>) {
        try {
            if (getSkuDetailsMethod == null) {
                getSkuDetailsMethod = getGetSkuDetailsMethod(IInAppBillingServiceClass)
                getSkuDetailsMethod!!.isAccessible = true
            }
            val querySkus = Bundle()
            querySkus.putStringArrayList("ITEM_ID_LIST", skusToAdd)
            val skuDetails = getSkuDetailsMethod!!.invoke(
                mIInAppBillingService,
                3,
                _applicationService.appContext.packageName,
                "inapp",
                querySkus
            ) as Bundle
            val response = skuDetails.getInt("RESPONSE_CODE")
            if (response == 0) {
                val responseList = skuDetails.getStringArrayList("DETAILS_LIST")
                val currentSkus: MutableMap<String, PurchaseInfo> = mutableMapOf()

                for (thisResponse in responseList!!) {
                    val `object` = JSONObject(thisResponse)
                    val sku = `object`.getString("productId")
                    val iso = `object`.getString("price_currency_code")
                    var price = BigDecimal(`object`.getString("price_amount_micros"))
                    price = price.divide(BigDecimal(1000000))

                    currentSkus[sku] = PurchaseInfo(sku, iso, price)
                }

                val purchasesToReport = mutableListOf<PurchaseInfo>()
                for (sku in skusToAdd) {
                    if (!currentSkus.containsKey(sku)) continue
                    purchasesToReport.add(currentSkus[sku]!!)
                }

                // New purchases to report. If successful then mark them as tracked.
                if (purchasesToReport.isNotEmpty()) {
                    _operationRepo.enqueue(TrackPurchaseOperation(_configModelStore.get().appId!!, _userSwitcher.identityModel.oneSignalId.toString(), newAsExisting, purchasesToReport))
                    purchaseTokens.addAll(newPurchaseTokens)
                    _prefs.saveString(PreferenceStores.PLAYER_PURCHASES, PreferencePlayerPurchasesKeys.PREFS_PURCHASE_TOKENS, purchaseTokens.toString())
                    _prefs.saveBool(PreferenceStores.PLAYER_PURCHASES, PreferencePlayerPurchasesKeys.PREFS_EXISTING_PURCHASES, true)
                    newAsExisting = false
                    isWaitingForPurchasesRequest = false
                }
            }
        } catch (t: Throwable) {
            Logging.warn("Failed to track IAP purchases", t)
        }
    }

    companion object {
        private var iapEnabled = -99
        private var IInAppBillingServiceClass: Class<*>? = null
        fun canTrack(context: Context): Boolean {
            if (iapEnabled == -99) iapEnabled =
                context.checkCallingOrSelfPermission("com.android.vending.BILLING")
            try {
                if (iapEnabled == PackageManager.PERMISSION_GRANTED) IInAppBillingServiceClass =
                    Class.forName("com.android.vending.billing.IInAppBillingService")
            } catch (t: Throwable) {
                iapEnabled = 0
                return false
            }
            return iapEnabled == PackageManager.PERMISSION_GRANTED
        }

        private fun getAsInterfaceMethod(clazz: Class<*>): Method? {
            for (method in clazz.methods) {
                val args = method.parameterTypes
                if (args.size == 1 && args[0] == IBinder::class.java) return method
            }
            return null
        }

        private fun getGetPurchasesMethod(clazz: Class<*>?): Method? {
            for (method in clazz!!.methods) {
                val args = method.parameterTypes
                if (args.size == 4 && args[0] == Int::class.javaPrimitiveType && args[1] == String::class.java && args[2] == String::class.java && args[3] == String::class.java) return method
            }
            return null
        }

        private fun getGetSkuDetailsMethod(clazz: Class<*>?): Method? {
            for (method in clazz!!.methods) {
                val args = method.parameterTypes
                val returnType = method.returnType
                if (args.size == 4 && args[0] == Int::class.javaPrimitiveType && args[1] == String::class.java && args[2] == String::class.java && args[3] == Bundle::class.java && returnType == Bundle::class.java) return method
            }
            return null
        }
    }
}
