package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import com.onesignal.sdktest.ui.theme.OneSignalRed
import org.json.JSONObject

private val TextFieldShape = RoundedCornerShape(10.dp)

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color(0xFFBDBDBD),
    focusedBorderColor = OneSignalRed,
    cursorColor = OneSignalRed,
    focusedLabelColor = OneSignalRed
)

private fun Modifier.applyTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

/**
 * Dialog for entering a single value.
 */
@Composable
fun SingleInputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    inputTestTag: String? = null,
    confirmTestTag: String? = "singleinput_confirm_button",
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth().applyTestTag(inputTestTag),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                shape = TextFieldShape,
                colors = dialogTextFieldColors()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.applyTestTag(confirmTestTag),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
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
    onConfirm: (String, String) -> Unit,
    keyTestTag: String? = null,
    valueTestTag: String? = null,
    confirmTestTag: String? = "singlepair_confirm_button",
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(keyLabel) },
                        modifier = Modifier.weight(1f).applyTestTag(keyTestTag),
                        singleLine = true,
                        shape = TextFieldShape,
                        colors = dialogTextFieldColors()
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(valueLabel) },
                        modifier = Modifier.weight(1f).applyTestTag(valueTestTag),
                        singleLine = true,
                        shape = TextFieldShape,
                        colors = dialogTextFieldColors()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { onConfirm(key, value) },
                        enabled = key.isNotBlank() && value.isNotBlank(),
                        modifier = Modifier.applyTestTag(confirmTestTag),
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    pairs.forEachIndexed { index, (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    pairs = pairs.toMutableList().apply {
                                        this[index] = Pair(newKey, this[index].second)
                                    }
                                },
                                label = { Text(keyLabel) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("multipair_key_$index"),
                                singleLine = true,
                                shape = TextFieldShape,
                                colors = dialogTextFieldColors()
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    pairs = pairs.toMutableList().apply {
                                        this[index] = Pair(this[index].first, newValue)
                                    }
                                },
                                label = { Text(valueLabel) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("multipair_value_$index"),
                                singleLine = true,
                                shape = TextFieldShape,
                                colors = dialogTextFieldColors()
                            )
                            if (pairs.size > 1) {
                                IconButton(
                                    onClick = {
                                        pairs = pairs.toMutableList().apply { removeAt(index) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove row",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
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
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("multipair_add_row_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Row")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { onConfirm(pairs) },
                        enabled = allValid,
                        modifier = Modifier.testTag("multipair_confirm_button"),
                    ) {
                        Text("Add All")
                    }
                }
            }
        }
    }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEach { (key, _) ->
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
                            .padding(vertical = 6.dp),
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
                            },
                            modifier = Modifier.testTag("remove_checkbox_$key"),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedKeys) },
                enabled = selectedKeys.isNotEmpty(),
                modifier = Modifier.testTag("multiselect_confirm_button"),
            ) {
                Text("Remove (${selectedKeys.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Dialog for login/switch user with optional JWT token.
 * Uses the same `singleinput_confirm_button` tag Capacitor's `SingleInputModal`
 * exposes so cross-platform E2E selectors line up on the login flow.
 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var externalId by remember { mutableStateOf("") }
    var jwtToken by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("Login User", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = externalId,
                    onValueChange = { externalId = it },
                    label = { Text("External User Id") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_user_id_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = jwtToken,
                    onValueChange = { jwtToken = it },
                    label = { Text("JWT Token (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_user_jwt_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(externalId, jwtToken.ifBlank { null }) },
                enabled = externalId.isNotBlank(),
                modifier = Modifier.testTag("singleinput_confirm_button"),
            ) {
                Text("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
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

    val outcomeTypes = listOf(
        Triple(0, "Normal Outcome", "outcome_type_normal_radio"),
        Triple(1, "Unique Outcome", "outcome_type_unique_radio"),
        Triple(2, "Outcome with Value", "outcome_type_value_radio")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("Send Outcome", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                outcomeTypes.forEach { (index, label, tag) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == index,
                            onClick = { selectedType = index },
                            modifier = Modifier.testTag(tag),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = outcomeName,
                    onValueChange = { outcomeName = it },
                    label = { Text("Outcome Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("outcome_name_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )

                if (selectedType == 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = outcomeValue,
                        onValueChange = { outcomeValue = it },
                        label = { Text("Value") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("outcome_value_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = TextFieldShape,
                        colors = dialogTextFieldColors()
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
                enabled = outcomeName.isNotBlank(),
                modifier = Modifier.testTag("outcome_send_button"),
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("Track Event", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_name_input"),
                    singleLine = true,
                    isError = eventName.isBlank() && showError,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = eventValue,
                    onValueChange = {
                        eventValue = it
                        showError = false
                    },
                    label = { Text("Properties (JSON, optional)") },
                    placeholder = { Text("{\"key\": \"value\"}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_properties_input"),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    isError = !isValueValid && eventValue.isNotBlank(),
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors(),
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
                enabled = eventName.isNotBlank() && isValueValid,
                modifier = Modifier.testTag("event_track_button"),
            ) {
                Text("Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
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
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("Custom Notification", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_notification_title_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_notification_body_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, body) },
                enabled = title.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.testTag("custom_notification_send_button"),
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("tooltip_title"),
            )
        },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("tooltip_description"),
                )
                options?.let { opts ->
                    Spacer(modifier = Modifier.height(12.dp))
                    opts.forEach { (name, desc) ->
                        Text(
                            text = "• $name: $desc",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tooltip_ok_button"),
            ) {
                Text("OK")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
