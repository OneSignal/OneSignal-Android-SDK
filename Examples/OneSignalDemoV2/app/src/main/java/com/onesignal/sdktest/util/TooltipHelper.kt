package com.onesignal.sdktest.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper object for loading and displaying tooltip content from JSON.
 * The tooltip content is fetched at runtime from the sdk-shared repo
 * and shared across all platform demo apps.
 */
object TooltipHelper {

    private var tooltips: Map<String, TooltipData> = emptyMap()
    private var initialized = false

    private const val TOOLTIP_URL =
        "https://raw.githubusercontent.com/OneSignal/sdk-shared/main/demo/tooltip_content.json"

    /**
     * Initialize the tooltip helper by fetching content from the sdk-shared repo on a background thread.
     * Call this once during app startup (e.g., in Application.onCreate()).
     * On failure (no network, etc.), tooltips are left empty — they are non-critical.
     */
    fun init(context: Context) {
        if (initialized) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(TOOLTIP_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

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
            } catch (e: Exception) {
                android.util.Log.e("TooltipHelper", "Failed to fetch tooltip content", e)
            }
        }
    }

    /**
     * Get tooltip data for a specific key.
     */
    fun getTooltip(key: String): TooltipData? {
        return tooltips[key]
    }

    /**
     * Show a tooltip dialog for the given key.
     */
    fun showTooltip(context: Context, key: String) {
        val tooltip = tooltips[key] ?: return
        
        val message = buildString {
            append(tooltip.description)
            
            tooltip.options?.let { options ->
                append("\n")
                options.forEach { option ->
                    append("\n• ${option.name}: ${option.description}")
                }
            }
        }
        
        AlertDialog.Builder(context)
            .setTitle(tooltip.title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
