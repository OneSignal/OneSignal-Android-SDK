package com.onesignal.sdktest.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper object for loading tooltip content from the sdk-shared repository.
 * Tooltip content is fetched at runtime from a remote URL, ensuring all SDK demo apps
 * share the same tooltip definitions.
 */
object TooltipHelper {

    private var tooltips: Map<String, TooltipData> = emptyMap()
    private var initialized = false
    
    private const val TOOLTIP_URL =
        "https://raw.githubusercontent.com/OneSignal/sdk-shared/main/demo/tooltip_content.json"

    /**
     * Initialize the tooltip helper by fetching content from remote URL on a background thread.
     * Call this once during app startup (e.g., in Application.onCreate()).
     * On failure (no network, etc.), tooltips remain empty — they are non-critical.
     */
    @Suppress("unused")
    fun init(context: Context) {
        if (initialized) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(TOOLTIP_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(json)
                    
                    val tooltipMap = mutableMapOf<String, TooltipData>()
                    
                    jsonObject.keys().forEach { key ->
                        val tooltipJson = jsonObject.getJSONObject(key)
                        val title = tooltipJson.getString("title")
                        val description = tooltipJson.getString("description")
                        
                        val options = if (tooltipJson.has("options")) {
                            val optionsArray = tooltipJson.getJSONArray("options")
                            (0 until optionsArray.length()).map { i ->
                                val optionJson = optionsArray.getJSONObject(i)
                                TooltipOption(
                                    name = optionJson.getString("name"),
                                    description = optionJson.getString("description")
                                )
                            }
                        } else {
                            null
                        }
                        
                        tooltipMap[key] = TooltipData(title, description, options)
                    }
                    
                    withContext(Dispatchers.Main) {
                        tooltips = tooltipMap
                        initialized = true
                    }
                }
            } catch (e: Exception) {
                // Tooltips are non-critical; log and continue
                android.util.Log.w("TooltipHelper", "Failed to fetch tooltip content: ${e.message}")
            }
        }
    }

    /**
     * Get tooltip data for a specific key.
     */
    fun getTooltip(key: String): TooltipData? {
        return tooltips[key]
    }
}

/**
 * Data class representing tooltip content.
 */
data class TooltipData(
    val title: String,
    val description: String,
    val options: List<TooltipOption>? = null
)

/**
 * Data class representing a tooltip option (for sections with multiple buttons).
 */
data class TooltipOption(
    val name: String,
    val description: String
)
