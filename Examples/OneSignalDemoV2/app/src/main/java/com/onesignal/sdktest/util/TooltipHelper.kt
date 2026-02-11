package com.onesignal.sdktest.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Helper object for loading tooltip content from JSON.
 * The tooltip content is stored in assets/tooltip_content.json for easy
 * sharing across SDK wrapper projects.
 */
object TooltipHelper {

    private var tooltips: Map<String, TooltipData> = emptyMap()
    private var initialized = false

    /**
     * Initialize the tooltip helper by loading content from assets on a background thread.
     * Call this once during app startup (e.g., in Application.onCreate()).
     */
    fun init(context: Context) {
        if (initialized) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = context.assets.open("tooltip_content.json").bufferedReader().use { it.readText() }
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
                android.util.Log.e("TooltipHelper", "Failed to load tooltip content", e)
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
