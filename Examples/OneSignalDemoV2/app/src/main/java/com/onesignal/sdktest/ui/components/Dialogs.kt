package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Dialog for entering a single value.
 */
@Composable
fun SingleInputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var value by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("ADD")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

/**
 * Dialog for entering a key-value pair.
 */
@Composable
fun PairInputDialog(
    title: String,
    keyLabel: String = "Key",
    valueLabel: String = "Value",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(keyLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(valueLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key, value) }) {
                Text("ADD")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

/**
 * Dialog for login/switch user.
 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    SingleInputDialog(
        title = "Login User",
        label = "External User Id",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * Dialog for outcome selection and input.
 */
@Composable
fun OutcomeDialog(
    onDismiss: () -> Unit,
    onSendNormal: (String) -> Unit,
    onSendUnique: (String) -> Unit,
    onSendWithValue: (String, Float) -> Unit
) {
    var selectedType by remember { mutableStateOf(0) }
    var outcomeName by remember { mutableStateOf("") }
    var outcomeValue by remember { mutableStateOf("") }
    
    val outcomeTypes = listOf("Normal Outcome", "Unique Outcome", "Outcome with Value")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Outcome") },
        text = {
            Column {
                // Outcome type selection
                outcomeTypes.forEachIndexed { index, type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == index,
                            onClick = { selectedType = index }
                        )
                        Text(
                            text = type,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = outcomeName,
                    onValueChange = { outcomeName = it },
                    label = { Text("Outcome Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (selectedType == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = outcomeValue,
                        onValueChange = { outcomeValue = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedType) {
                        0 -> onSendNormal(outcomeName)
                        1 -> onSendUnique(outcomeName)
                        2 -> onSendWithValue(outcomeName, outcomeValue.toFloatOrNull() ?: 0f)
                    }
                }
            ) {
                Text("SEND")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

/**
 * Validates if a string is valid JSON object.
 */
private fun isValidJsonObject(value: String?): Boolean {
    if (value.isNullOrBlank()) return true // Empty is valid (optional field)
    return try {
        JSONObject(value)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Parses a JSON string into a Map.
 */
private fun parseJsonToMap(json: String): Map<String, Any>? {
    if (json.isBlank()) return null
    return try {
        val jsonObject = JSONObject(json)
        val map = mutableMapOf<String, Any>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.get(key)
        }
        map
    } catch (e: Exception) {
        null
    }
}

/**
 * Dialog for track event with JSON validation.
 */
@Composable
fun TrackEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Map<String, Any>?) -> Unit
) {
    var eventName by remember { mutableStateOf("") }
    var eventValue by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    // Validate on each change
    val isValueValid = isValidJsonObject(eventValue)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track Event") },
        text = {
            Column {
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = eventName.isBlank() && showError
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = eventValue,
                    onValueChange = { 
                        eventValue = it
                        showError = false // Clear error on change
                    },
                    label = { Text("Properties (JSON, optional)") },
                    placeholder = { Text("{\"key\": \"value\"}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    isError = !isValueValid && eventValue.isNotBlank(),
                    supportingText = if (!isValueValid && eventValue.isNotBlank()) {
                        { 
                            Text(
                                text = "Invalid JSON format. Example: {\"key\": \"value\"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (eventValue.isBlank()) {
                        {
                            Text(
                                text = "Optional: Enter a valid JSON object",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    showError = true
                    if (eventName.isNotBlank() && isValueValid) {
                        val properties = parseJsonToMap(eventValue)
                        onConfirm(eventName, properties)
                    }
                },
                enabled = eventName.isNotBlank() && isValueValid
            ) {
                Text("TRACK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

/**
 * Dialog for custom notification.
 */
@Composable
fun CustomNotificationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    PairInputDialog(
        title = "Custom Notification",
        keyLabel = "Title",
        valueLabel = "Body",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * Tooltip info dialog.
 */
@Composable
fun TooltipDialog(
    title: String,
    description: String,
    options: List<Pair<String, String>>? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description)
                options?.let { opts ->
                    Spacer(modifier = Modifier.height(12.dp))
                    opts.forEach { (name, desc) ->
                        Text(
                            text = "• $name: $desc",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
