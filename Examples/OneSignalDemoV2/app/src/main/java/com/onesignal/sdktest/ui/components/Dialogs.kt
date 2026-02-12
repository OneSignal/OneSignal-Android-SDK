package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
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
            TextButton(
                onClick = { onConfirm(key, value) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
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
 * Dialog for entering multiple key-value pairs.
 */
@Composable
fun MultiPairInputDialog(
    title: String,
    keyLabel: String = "Key",
    valueLabel: String = "Value",
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, String>>) -> Unit
) {
    var pairs by remember { mutableStateOf(listOf(Pair("", ""))) }
    
    val allValid = pairs.all { it.first.isNotBlank() && it.second.isNotBlank() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                pairs.forEachIndexed { index, (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    pairs = pairs.toMutableList().apply {
                                        this[index] = Pair(newKey, this[index].second)
                                    }
                                },
                                label = { Text(keyLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    pairs = pairs.toMutableList().apply {
                                        this[index] = Pair(this[index].first, newValue)
                                    }
                                },
                                label = { Text(valueLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        if (pairs.size > 1) {
                            IconButton(
                                onClick = {
                                    pairs = pairs.toMutableList().apply { removeAt(index) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (index < pairs.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = { pairs = pairs + Pair("", "") },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD ROW")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pairs) },
                enabled = allValid
            ) {
                Text("ADD ALL")
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
 * Dialog for selecting multiple items to remove.
 */
@Composable
fun MultiSelectRemoveDialog(
    title: String,
    items: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (Collection<String>) -> Unit
) {
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEach { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedKeys = if (key in selectedKeys) {
                                    selectedKeys - key
                                } else {
                                    selectedKeys + key
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = key in selectedKeys,
                            onCheckedChange = { checked ->
                                selectedKeys = if (checked) {
                                    selectedKeys + key
                                } else {
                                    selectedKeys - key
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$key: $value",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedKeys) },
                enabled = selectedKeys.isNotEmpty()
            ) {
                Text("REMOVE (${selectedKeys.size})")
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
                outcomeTypes.forEachIndexed { index, type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
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
                },
                enabled = outcomeName.isNotBlank()
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

private fun isValidJsonObject(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    return try {
        JSONObject(value)
        true
    } catch (e: Exception) {
        false
    }
}

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
                        showError = false
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
                                text = "Invalid JSON format",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null
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
