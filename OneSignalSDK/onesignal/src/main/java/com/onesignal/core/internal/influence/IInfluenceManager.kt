package com.onesignal.core.internal.influence

import com.onesignal.core.internal.AppEntryAction
import com.onesignal.core.internal.params.IParamsService
import org.json.JSONObject

interface IInfluenceManager {
    val influences: List<Influence>
    val sessionInfluences: List<Influence>
    val iAMChannelTracker: IChannelTracker
    val notificationChannelTracker: IChannelTracker
    val channels: List<IChannelTracker>

    fun initFromCache()
    fun saveInfluenceParams(influenceParams: IParamsService.InfluenceParams)
    fun addSessionData(jsonObject: JSONObject, influences: List<Influence>)
    fun getChannelByEntryAction(entryAction: AppEntryAction): IChannelTracker?
    fun getChannelsToResetByEntryAction(entryAction: AppEntryAction): List<IChannelTracker>
}
