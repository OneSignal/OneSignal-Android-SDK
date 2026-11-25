package com.onesignal.example.ui.components

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.onesignal.example.data.model.CompositeLoginRequest
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsCardBackground
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsGrey700
import com.onesignal.example.ui.theme.OsPrimary
import org.json.JSONObject

private val TextFieldShape = RoundedCornerShape(DemoLayout.inputRadius)
private val DialogShape = RoundedCornerShape(DemoLayout.dialogRadius)
private val DialogVerticalInset = 24.dp
private const val BACKDROP_ALPHA = 0.54f

// JWTs run to hundreds of characters; wrapping lets testers see what they pasted.
private const val JWT_MIN_LINES = 3
private const val JWT_MAX_LINES = 5

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = OsGrey700,
    focusedBorderColor = OsPrimary,
    cursorColor = OsPrimary,
    focusedLabelColor = OsPrimary,
)

// OutlinedTextField enforces a 56dp minimum height; this keeps its look at a denser size
// so dialogs need less scrolling in Appium runs.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = TextFieldShape,
    colors: TextFieldColors = dialogTextFieldColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else OsPrimary),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                placeholder = placeholder,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                colors = colors,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                    )
                },
            )
        },
    )
}

@Composable
private fun dialogTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = OsPrimary,
    disabledContentColor = OsGrey500,
)

@Composable
private fun DialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)
}

@Composable
private fun DialogActionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private fun Modifier.applyTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

// Compose dialogs render into a separate window (a sibling ViewRootImpl), so
// the `testTagsAsResourceId=true` semantics flag we set at MainActivity's
// root does NOT propagate into dialog content. Without re-applying it here,
// every testTag inside a dialog stays invisible to UiAutomator2/Appium's
// `id=foo` selector. Chain this helper onto each dialog's outermost modifier.
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.exposeTestTagsAsResourceId(): Modifier =
    this.semantics { testTagsAsResourceId = true }

/**
 * Full-width dialog anchored below the safe area per styles.md "Dialogs".
 *
 * The dialog window fills the space between the system bars so the card's
 * top edge never moves. When the keyboard opens only the bottom bound shrinks
 * and the content scrolls, which keeps Appium element frames stable while the
 * action row stays above the keyboard.
 */
@Composable
private fun DemoDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // The system dim covers the status and navigation bars, which the dialog window
            // itself doesn't reach, so it serves as the osBackdrop.
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(BACKDROP_ALPHA)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .exposeTestTagsAsResourceId(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = DemoLayout.pagePadding, vertical = DialogVerticalInset)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top),
                shape = DialogShape,
                color = OsCardBackground,
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    ) {
                        title()
                        Spacer(modifier = Modifier.height(12.dp))
                        text()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(DemoLayout.gap, Alignment.End),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle(title)
        },
        text = {
            DemoTextField(
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DemoTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(keyLabel) },
                    modifier = Modifier.weight(1f).applyTestTag(keyTestTag),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                DemoTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(valueLabel) },
                    modifier = Modifier.weight(1f).applyTestTag(valueTestTag),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key, value) },
                enabled = key.isNotBlank() && value.isNotBlank(),
                modifier = Modifier.applyTestTag(confirmTestTag),
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                pairs.forEachIndexed { index, (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DemoTextField(
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
                        DemoTextField(
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
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pairs) },
                enabled = allValid,
                modifier = Modifier.testTag("multipair_confirm_button"),
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Add All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle(title)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CompactControls {
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedKeys) },
                enabled = selectedKeys.isNotEmpty(),
                modifier = Modifier.testTag("multiselect_confirm_button"),
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Remove (${selectedKeys.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Login User")
        },
        text = {
            Column {
                DemoTextField(
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
                DemoTextField(
                    value = jwtToken,
                    onValueChange = { jwtToken = it },
                    label = { Text("JWT Token (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_user_jwt_input"),
                    minLines = JWT_MIN_LINES,
                    maxLines = JWT_MAX_LINES,
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
    )
}

@Composable
fun UpdateUserJwtDialog(
    initialExternalId: String,
    onDismiss: () -> Unit,
    onConfirm: (externalId: String, token: String) -> Unit,
) {
    var externalId by remember { mutableStateOf(initialExternalId) }
    var token by remember { mutableStateOf("") }

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Update User JWT")
        },
        text = {
            Column {
                DemoTextField(
                    value = externalId,
                    onValueChange = { externalId = it },
                    label = { Text("External User Id") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_jwt_external_id_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                DemoTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("JWT Token") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_jwt_token_input"),
                    minLines = JWT_MIN_LINES,
                    maxLines = JWT_MAX_LINES,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(externalId, token) },
                enabled = externalId.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.testTag("update_jwt_confirm_button"),
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
    )
}

@Composable
fun CompositeLoginDialog(
    onDismiss: () -> Unit,
    onConfirm: (CompositeLoginRequest) -> Unit,
) {
    var externalId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf("") }
    var jwtToken by remember { mutableStateOf("") }
    var emailEnabled by remember { mutableStateOf<Boolean?>(null) }
    var smsEnabled by remember { mutableStateOf<Boolean?>(null) }
    var tagKey by remember { mutableStateOf("") }
    var tagValue by remember { mutableStateOf("") }
    var aliasLabel by remember { mutableStateOf("") }
    var aliasId by remember { mutableStateOf("") }

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Login With Profile")
        },
        text = {
            Column {
                DemoTextField(
                    value = externalId,
                    onValueChange = { externalId = it },
                    label = { Text("External User Id") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composite_login_user_id_input"),
                    singleLine = true,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                DemoTextField(
                    value = jwtToken,
                    onValueChange = { jwtToken = it },
                    label = { Text("JWT Token (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composite_login_jwt_input"),
                    minLines = JWT_MIN_LINES,
                    maxLines = JWT_MAX_LINES,
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                DemoTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composite_login_email_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SubscriptionStatusField(
                    label = "Email status",
                    value = emailEnabled,
                    onValueChange = { emailEnabled = it },
                    testTag = "composite_login_email_enabled_input",
                )
                Spacer(modifier = Modifier.height(12.dp))
                DemoTextField(
                    value = sms,
                    onValueChange = { sms = it },
                    label = { Text("SMS (optional)") },
                    // The SDK rejects numbers that aren't E.164.
                    placeholder = { Text("+14155552671") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composite_login_sms_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = TextFieldShape,
                    colors = dialogTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SubscriptionStatusField(
                    label = "SMS status",
                    value = smsEnabled,
                    onValueChange = { smsEnabled = it },
                    testTag = "composite_login_sms_enabled_input",
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProfilePairRow(
                    caption = "Tag (optional)",
                    keyLabel = "Key",
                    key = tagKey,
                    onKeyChange = { tagKey = it },
                    keyTestTag = "composite_login_tag_key_input",
                    valueLabel = "Value",
                    value = tagValue,
                    onValueChange = { tagValue = it },
                    valueTestTag = "composite_login_tag_value_input",
                )
                Spacer(modifier = Modifier.height(12.dp))
                ProfilePairRow(
                    caption = "Alias (optional)",
                    keyLabel = "Label",
                    key = aliasLabel,
                    onKeyChange = { aliasLabel = it },
                    keyTestTag = "composite_login_alias_label_input",
                    valueLabel = "ID",
                    value = aliasId,
                    onValueChange = { aliasId = it },
                    valueTestTag = "composite_login_alias_id_input",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CompositeLoginRequest(
                            externalId = externalId,
                            jwt = jwtToken.ifBlank { null },
                            email = email.ifBlank { null },
                            emailEnabled = emailEnabled.takeIf { email.isNotBlank() },
                            sms = sms.ifBlank { null },
                            smsEnabled = smsEnabled.takeIf { sms.isNotBlank() },
                            tags = completePair(tagKey, tagValue),
                            aliases = completePair(aliasLabel, aliasId),
                        ),
                    )
                },
                enabled = externalId.isNotBlank() &&
                    !isHalfFilled(tagKey, tagValue) &&
                    !isHalfFilled(aliasLabel, aliasId),
                modifier = Modifier.testTag("composite_login_confirm_button"),
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
    )
}

private fun completePair(key: String, value: String): Map<String, String> =
    if (key.isNotBlank() && value.isNotBlank()) mapOf(key to value) else emptyMap()

// The SDK rejects empty keys and values, so a row must be fully filled or left empty.
private fun isHalfFilled(key: String, value: String): Boolean = key.isNotBlank() != value.isNotBlank()

private const val HALF_FILLED_HINT = "Fill both or leave both empty"

@Composable
private fun ProfilePairRow(
    caption: String,
    keyLabel: String,
    key: String,
    onKeyChange: (String) -> Unit,
    keyTestTag: String,
    valueLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    valueTestTag: String,
) {
    val keyMissing = key.isBlank() && value.isNotBlank()
    val valueMissing = value.isBlank() && key.isNotBlank()
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = OsGrey700,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text(keyLabel) },
            isError = keyMissing,
            supportingText = if (keyMissing) { { Text(HALF_FILLED_HINT) } } else null,
            modifier = Modifier.weight(1f).testTag(keyTestTag),
            singleLine = true,
            shape = TextFieldShape,
            colors = dialogTextFieldColors(),
        )
        DemoTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(valueLabel) },
            isError = valueMissing,
            supportingText = if (valueMissing) { { Text(HALF_FILLED_HINT) } } else null,
            modifier = Modifier.weight(1f).testTag(valueTestTag),
            singleLine = true,
            shape = TextFieldShape,
            colors = dialogTextFieldColors(),
        )
    }
}

// Drops Material's 48dp touch target around radios and checkboxes; their rows are tappable instead.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactControls(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false, content = content)
}

// "Default" sends no status: an existing address keeps its own, a new one gets the backend default.
private val subscriptionStatusOptions = listOf(
    "Default" to null,
    "Subscribed" to true,
    "Unsubscribed" to false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionStatusField(
    label: String,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        DemoTextField(
            value = subscriptionStatusOptions.first { it.second == value }.first,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .testTag(testTag),
            singleLine = true,
            shape = TextFieldShape,
            colors = dialogTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            subscriptionStatusOptions.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Send Outcome")
        },
        text = {
            Column {
                CompactControls {
                    outcomeTypes.forEach { (index, label, tag) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedType == index, onClick = { selectedType = index })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == index,
                                onClick = { selectedType = index },
                                modifier = Modifier.testTag(tag),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                DemoTextField(
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
                    Spacer(modifier = Modifier.height(12.dp))
                    DemoTextField(
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Track Event")
        },
        text = {
            Column {
                DemoTextField(
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

                Spacer(modifier = Modifier.height(12.dp))

                DemoTextField(
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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

    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle("Custom Notification")
        },
        text = {
            Column {
                DemoTextField(
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
                Spacer(modifier = Modifier.height(12.dp))
                DemoTextField(
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = dialogTextButtonColors()) {
                DialogActionLabel("Cancel")
            }
        },
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
    DemoDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
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
                colors = dialogTextButtonColors(),
            ) {
                DialogActionLabel("OK")
            }
        },
    )
}
