package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.onesignal.core.internal.operations.DeleteUserOperation
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.operations.TrackPurchaseOperation
import com.onesignal.onesignal.core.internal.operations.UpdateUserOperation
import org.json.JSONArray
import org.json.JSONObject

class UserOperationExecutor(
    private val _http: IHttpClient
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_USER, UPDATE_USER, DELETE_USER, TRACK_PURCHASE)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "UserOperationExecutor(operation: $operation)")

        when (operation) {
            is CreateUserOperation -> {
                //            _http.createUserAsync(null)
            }
            is DeleteUserOperation -> {
                //            _http.deleteUserAsync("external_id", operation.id)
            }
            is UpdateUserOperation -> {
                //            _http.updateUserAsync("external_id", operation.id, null)
            }
            is TrackPurchaseOperation -> {
                val purchasesArray = JSONArray()
                for (purchase in operation.purchases) {
                    val jsonItem = JSONObject()
                    jsonItem.put("sku", purchase.sku)
                    jsonItem.put("iso", purchase.iso)
                    jsonItem.put("amount", purchase.amount.toString())
                    purchasesArray.put(jsonItem)
                }
                // TODO: put purchaseArray into the update user payload.
            }
        }
    }

    companion object {
        const val CREATE_USER = "create-user"
        const val UPDATE_USER = "update-user"
        const val DELETE_USER = "delete-user"
        const val TRACK_PURCHASE = "track-purchase"
    }
}
