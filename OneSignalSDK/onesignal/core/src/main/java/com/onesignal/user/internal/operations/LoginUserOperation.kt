package com.onesignal.user.internal.operations

import com.onesignal.OneSignalUserProfile
import com.onesignal.common.IDManager
import com.onesignal.common.putMap
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to login the user with the [externalId] provided.  Logging in a user will do the
 * following:
 *
 * 1. Attempt to give the user identified by [existingOnesignalId] an alias of [externalId]. If
 *    this succeeds the existing user becomes
 */
class LoginUserOperation() : Operation(LoginUserOperationExecutor.LOGIN_USER) {
    /**
     * The application ID the user will exist/be logged in under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The local OneSignal ID this user was initially logged in under. The user models with this ID
     * will have its ID updated with the backend-generated ID post-create.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * The user ID of an existing user the [externalId] will be attempted to be associated to first.
     * When null (or non-null but unsuccessful), a new user will be upserted. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var existingOnesignalId: String?
        get() = getOptStringProperty(::existingOnesignalId.name)
        internal set(value) { // `internal` so OperationRepo can merge during de-dupe
            setOptStringProperty(::existingOnesignalId.name, value)
        }

    internal var email: String?
        get() = getOptStringProperty(PROFILE_EMAIL)
        set(value) {
            setOptStringProperty(PROFILE_EMAIL, value)
        }

    internal var phoneNumber: String?
        get() = getOptStringProperty(PROFILE_PHONE)
        set(value) {
            setOptStringProperty(PROFILE_PHONE, value)
        }

    internal var tags: Map<String, String>
        get() = decodeMap(getOptStringProperty(PROFILE_TAGS))
        set(value) {
            setOptStringProperty(PROFILE_TAGS, encodeMap(value))
        }

    internal var aliases: Map<String, String>
        get() = decodeMap(getOptStringProperty(PROFILE_ALIASES))
        set(value) {
            setOptStringProperty(PROFILE_ALIASES, encodeMap(value))
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String = ""
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.CREATE
    override val canStartExecute: Boolean get() = existingOnesignalId == null || !IDManager.isLocalId(existingOnesignalId!!)
    override val applyToRecordId: String get() = existingOnesignalId ?: onesignalId

    constructor(
        appId: String,
        onesignalId: String,
        externalId: String?,
        existingOneSignalId: String? = null,
        profile: OneSignalUserProfile? = null,
    ) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.externalId = externalId
        this.existingOnesignalId = existingOneSignalId
        if (profile != null) {
            this.email = profile.email
            this.phoneNumber = profile.phoneNumber
            this.tags = profile.tags
            this.aliases = profile.aliases
        }
    }

    internal fun hasProfileFields(): Boolean =
        !email.isNullOrBlank() || !phoneNumber.isNullOrBlank() || tags.isNotEmpty() || aliases.isNotEmpty()

    internal fun mergeProfileFrom(other: LoginUserOperation) {
        if (email.isNullOrEmpty()) email = other.email
        if (phoneNumber.isNullOrEmpty()) phoneNumber = other.phoneNumber
        if (other.tags.isNotEmpty()) tags = tags + other.tags
        if (other.aliases.isNotEmpty()) aliases = aliases + other.aliases
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(existingOnesignalId)) {
            existingOnesignalId = map[existingOnesignalId]!!
        }
    }
}

internal fun reservedLoginAliasLabel(label: String): Boolean =
    label.isEmpty() || label == IdentityConstants.ONESIGNAL_ID || label == IdentityConstants.EXTERNAL_ID

private const val PROFILE_EMAIL = "profileEmail"
private const val PROFILE_PHONE = "profilePhoneNumber"
private const val PROFILE_TAGS = "profileTags"
private const val PROFILE_ALIASES = "profileAliases"

private fun encodeMap(map: Map<String, String>): String? {
    if (map.isEmpty()) return null
    return JSONObject().putMap(map).toString()
}

private fun decodeMap(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    val obj = JSONObject(json)
    val result = mutableMapOf<String, String>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = obj.optString(key)
    }
    return result
}
