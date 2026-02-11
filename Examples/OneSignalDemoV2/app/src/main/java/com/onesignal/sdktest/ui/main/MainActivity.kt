package com.onesignal.sdktest.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.onesignal.sdktest.R
import com.onesignal.sdktest.data.model.InAppMessageType
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.databinding.ActivityMainBinding
import com.onesignal.sdktest.ui.adapter.InAppMessageAdapter
import com.onesignal.sdktest.ui.adapter.PairListAdapter
import com.onesignal.sdktest.ui.adapter.SingleListAdapter
import com.onesignal.sdktest.ui.secondary.SecondaryActivity
import com.onesignal.sdktest.util.TooltipHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Adapters
    private lateinit var aliasesAdapter: PairListAdapter
    private lateinit var emailsAdapter: SingleListAdapter
    private lateinit var smsAdapter: SingleListAdapter
    private lateinit var tagsAdapter: PairListAdapter
    private lateinit var triggersAdapter: PairListAdapter
    private lateinit var inAppMessagesAdapter: InAppMessageAdapter

    // Collapse state for lists
    private var emailsExpanded = false
    private var smsExpanded = false
    private var allEmails: List<String> = emptyList()
    private var allSmsNumbers: List<String> = emptyList()

    companion object {
        private const val MAX_COLLAPSED_ITEMS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupAdapters()
        setupRecyclerViews()
        setupClickListeners()
        setupObservers()
        
        // Automatically prompt for notification permission when activity loads
        viewModel.promptPush()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupAdapters() {
        aliasesAdapter = PairListAdapter { key -> viewModel.removeAlias(key) }
        emailsAdapter = SingleListAdapter { email -> viewModel.removeEmail(email) }
        smsAdapter = SingleListAdapter { sms -> viewModel.removeSms(sms) }
        tagsAdapter = PairListAdapter { key -> viewModel.removeTag(key) }
        triggersAdapter = PairListAdapter { key -> viewModel.removeTrigger(key) }

        inAppMessagesAdapter = InAppMessageAdapter(InAppMessageType.values().toList()) { type ->
            viewModel.sendInAppMessage(type.triggerKey, type.triggerValue)
        }
    }

    private fun setupRecyclerViews() {
        // Aliases
        binding.rvAliases.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = aliasesAdapter
        }

        // Emails
        binding.rvEmails.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = emailsAdapter
        }

        // SMS
        binding.rvSms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsAdapter
        }

        // Tags
        binding.rvTags.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tagsAdapter
        }

        // Triggers
        binding.rvTriggers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = triggersAdapter
        }

        // In-App Messages (full-width buttons)
        binding.rvInAppMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = inAppMessagesAdapter
        }
    }

    private fun setupClickListeners() {
        // Guidance banner link
        binding.tvGetKeysLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onesignal.com"))
            startActivity(intent)
        }

        // Info tooltip buttons
        binding.btnInfoAliases.setOnClickListener {
            TooltipHelper.showTooltip(this, "aliases")
        }
        binding.btnInfoPush.setOnClickListener {
            TooltipHelper.showTooltip(this, "push")
        }
        binding.btnInfoSendPush.setOnClickListener {
            TooltipHelper.showTooltip(this, "sendPushNotification")
        }
        binding.btnInfoSendIam.setOnClickListener {
            TooltipHelper.showTooltip(this, "sendInAppMessage")
        }
        binding.btnInfoEmails.setOnClickListener {
            TooltipHelper.showTooltip(this, "emails")
        }
        binding.btnInfoSms.setOnClickListener {
            TooltipHelper.showTooltip(this, "sms")
        }
        binding.btnInfoTags.setOnClickListener {
            TooltipHelper.showTooltip(this, "tags")
        }
        binding.btnInfoOutcomes.setOnClickListener {
            TooltipHelper.showTooltip(this, "outcomes")
        }
        binding.btnInfoIam.setOnClickListener {
            TooltipHelper.showTooltip(this, "inAppMessaging")
        }
        binding.btnInfoTriggers.setOnClickListener {
            TooltipHelper.showTooltip(this, "triggers")
        }
        binding.btnInfoTrackEvent.setOnClickListener {
            TooltipHelper.showTooltip(this, "trackEvent")
        }
        binding.btnInfoLocation.setOnClickListener {
            TooltipHelper.showTooltip(this, "location")
        }

        // Privacy Consent toggle
        binding.switchPrivacyConsent.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPrivacyConsent(isChecked)
        }

        // Login/Logout
        binding.btnLoginUser.setOnClickListener {
            showLoginDialog()
        }

        binding.btnLogoutUser.setOnClickListener {
            viewModel.logoutUser()
        }

        // Add buttons
        binding.btnAddAlias.setOnClickListener {
            showAddPairDialog(getString(R.string.add_alias_title)) { key, value ->
                viewModel.addAlias(key, value)
            }
        }

        binding.btnRemoveAllAliases.setOnClickListener {
            viewModel.removeAllAliases()
        }

        binding.btnAddEmail.setOnClickListener {
            showSingleInputDialog(getString(R.string.new_email)) { email ->
                viewModel.addEmail(email)
            }
        }

        binding.btnAddSms.setOnClickListener {
            showSingleInputDialog(getString(R.string.new_sms)) { sms ->
                viewModel.addSms(sms)
            }
        }

        binding.btnAddTag.setOnClickListener {
            showAddPairDialog(getString(R.string.add_tag_title)) { key, value ->
                viewModel.addTag(key, value)
            }
        }

        binding.btnAddTrigger.setOnClickListener {
            showAddPairDialog(getString(R.string.add_trigger_title)) { key, value ->
                viewModel.addTrigger(key, value)
            }
        }

        binding.btnClearTriggers.setOnClickListener {
            viewModel.clearTriggers()
        }

        binding.btnSendOutcome.setOnClickListener {
            showOutcomeDialog()
        }

        // Switches
        binding.switchPushEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPushEnabled(isChecked)
        }

        binding.switchPauseIam.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setInAppMessagesPaused(isChecked)
        }

        binding.switchLocationShared.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setLocationShared(isChecked)
        }

        // Track Event
        binding.btnTrackEvent.setOnClickListener {
            showTrackEventDialog()
        }

        // Send Notifications
        binding.btnSendSimpleNotification.setOnClickListener {
            viewModel.sendNotification(NotificationType.SIMPLE)
        }

        binding.btnSendImageNotification.setOnClickListener {
            viewModel.sendNotification(NotificationType.WITH_IMAGE)
        }

        binding.btnSendCustomNotification.setOnClickListener {
            showCustomNotificationDialog()
        }

        // Location prompt
        binding.btnPromptLocation.setOnClickListener {
            viewModel.promptLocation()
        }

        // Prompt Push
        binding.btnPromptPush.setOnClickListener {
            viewModel.promptPush()
        }

        // Next Activity
        binding.btnNextActivity.setOnClickListener {
            startActivity(Intent(this, SecondaryActivity::class.java))
        }
    }

    private fun setupObservers() {
        // App ID
        viewModel.appId.observe(this) { appId ->
            binding.tvAppId.text = appId
        }

        // Push Subscription
        viewModel.pushSubscriptionId.observe(this) { pushId ->
            binding.tvPushId.text = pushId ?: "Not Available"
        }

        viewModel.pushEnabled.observe(this) { enabled ->
            binding.switchPushEnabled.isChecked = enabled
        }

        // Notification Permission - show/hide prompt push button
        viewModel.hasNotificationPermission.observe(this) { hasPermission ->
            binding.layoutPromptPush.visibility = if (hasPermission) View.GONE else View.VISIBLE
            binding.switchPushEnabled.isEnabled = hasPermission
        }

        // Privacy Consent
        viewModel.privacyConsentGiven.observe(this) { granted ->
            binding.switchPrivacyConsent.isChecked = granted
        }

        // Aliases
        viewModel.aliases.observe(this) { aliases ->
            android.util.Log.d("MainActivity", "Aliases observer triggered: ${aliases.size} items")
            aliasesAdapter.submitList(aliases)
            val hasItems = aliases.isNotEmpty()
            binding.rvAliases.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.tvNoAliases.visibility = if (hasItems) View.GONE else View.VISIBLE
            binding.layoutRemoveAllAliases.visibility = if (hasItems) View.VISIBLE else View.GONE
        }

        // Emails
        viewModel.emails.observe(this) { emails ->
            android.util.Log.d("MainActivity", "Emails observer triggered: ${emails.size} items")
            allEmails = emails
            updateEmailsDisplay()
        }

        // SMS
        viewModel.smsNumbers.observe(this) { smsNumbers ->
            allSmsNumbers = smsNumbers
            updateSmsDisplay()
        }

        // Tags
        viewModel.tags.observe(this) { tags ->
            tagsAdapter.submitList(tags)
            val hasItems = tags.isNotEmpty()
            binding.rvTags.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.tvNoTags.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        // Triggers
        viewModel.triggers.observe(this) { triggers ->
            triggersAdapter.submitList(triggers)
            binding.rvTriggers.visibility = if (triggers.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoTriggers.visibility = if (triggers.isEmpty()) View.VISIBLE else View.GONE
            // Show Clear Triggers when at least 1 trigger exists
            binding.layoutClearTriggers.visibility = if (triggers.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // In-App Messages Paused
        viewModel.inAppMessagesPaused.observe(this) { paused ->
            binding.switchPauseIam.isChecked = paused
        }

        // Location Shared
        viewModel.locationShared.observe(this) { shared ->
            binding.switchLocationShared.isChecked = shared
        }

        // Toast messages
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }

        // Loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // External User ID (login state)
        viewModel.externalUserId.observe(this) { externalId ->
            val isLoggedIn = !externalId.isNullOrEmpty()
            binding.btnLoginUser.text = if (isLoggedIn) {
                getString(R.string.switch_user)
            } else {
                getString(R.string.login_user)
            }
            binding.layoutExternalId.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            binding.tvExternalId.text = externalId ?: ""
        }
    }

    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val etExternalUserId = dialogView.findViewById<EditText>(R.id.et_external_user_id)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.external_user_id)
            .setView(dialogView)
            .setPositiveButton(R.string.login) { _, _ ->
                val externalUserId = etExternalUserId.text.toString().trim()
                if (externalUserId.isNotEmpty()) {
                    viewModel.loginUser(externalUserId)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddPairDialog(title: String, onAdd: (String, String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pair, null)
        val etKey = dialogView.findViewById<EditText>(R.id.et_key)
        val etValue = dialogView.findViewById<EditText>(R.id.et_value)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val key = etKey.text.toString().trim()
                val value = etValue.text.toString().trim()
                if (key.isNotEmpty()) {
                    onAdd(key, value)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSingleInputDialog(label: String, onAdd: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_input, null)
        val etInput = dialogView.findViewById<EditText>(R.id.et_input)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(label)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val input = etInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    onAdd(input)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTrackEventDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pair, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_key)
        val etValue = dialogView.findViewById<EditText>(R.id.et_value)

        // Update hints for track event context
        etName.hint = getString(R.string.event_name)
        etValue.hint = getString(R.string.event_value)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.track_event)
            .setView(dialogView)
            .setPositiveButton(R.string.send) { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.trackEvent(name, value.ifEmpty { null })
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomNotificationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pair, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_key)
        val etBody = dialogView.findViewById<EditText>(R.id.et_value)

        // Update hints for notification context
        etTitle.hint = getString(R.string.notification_title)
        etBody.hint = getString(R.string.notification_body)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.send_custom_notification)
            .setView(dialogView)
            .setPositiveButton(R.string.send) { _, _ ->
                val title = etTitle.text.toString().trim()
                val body = etBody.text.toString().trim()
                if (title.isNotEmpty() && body.isNotEmpty()) {
                    viewModel.sendCustomNotification(title, body)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showOutcomeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_outcome, null)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_outcome_type)
        val etName = dialogView.findViewById<EditText>(R.id.et_outcome_name)
        val tvValueLabel = dialogView.findViewById<TextView>(R.id.tv_outcome_value_label)
        val etValue = dialogView.findViewById<EditText>(R.id.et_outcome_value)

        // Outcome types
        val outcomeTypes = arrayOf(
            getString(R.string.select_outcome_type),
            getString(R.string.normal_outcome),
            getString(R.string.unique_outcome),
            getString(R.string.outcome_with_value)
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, outcomeTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        // Show/hide value field based on selection
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val showValue = position == 3 // "Outcome with Value"
                tvValueLabel.visibility = if (showValue) View.VISIBLE else View.GONE
                etValue.visibility = if (showValue) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                tvValueLabel.visibility = View.GONE
                etValue.visibility = View.GONE
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.outcome_events)
            .setView(dialogView)
            .setPositiveButton(R.string.send) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton

                when (spinnerType.selectedItemPosition) {
                    1 -> viewModel.sendOutcome(name) // Normal Outcome
                    2 -> viewModel.sendUniqueOutcome(name) // Unique Outcome
                    3 -> { // Outcome with Value
                        val valueStr = etValue.text.toString().trim()
                        val value = valueStr.toFloatOrNull() ?: 0f
                        viewModel.sendOutcomeWithValue(name, value)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPushSubscription()
    }

    private fun updateEmailsDisplay() {
        val hasItems = allEmails.isNotEmpty()
        binding.rvEmails.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvNoEmails.visibility = if (hasItems) View.GONE else View.VISIBLE

        if (allEmails.size > MAX_COLLAPSED_ITEMS && !emailsExpanded) {
            // Show collapsed view
            emailsAdapter.submitList(allEmails.take(MAX_COLLAPSED_ITEMS))
            val remaining = allEmails.size - MAX_COLLAPSED_ITEMS
            binding.tvEmailsMore.text = getString(R.string.x_more_available, remaining)
            binding.tvEmailsMore.visibility = View.VISIBLE
            binding.tvEmailsMore.setOnClickListener {
                emailsExpanded = true
                updateEmailsDisplay()
            }
        } else {
            // Show all items
            emailsAdapter.submitList(allEmails)
            binding.tvEmailsMore.visibility = View.GONE
        }
    }

    private fun updateSmsDisplay() {
        val hasItems = allSmsNumbers.isNotEmpty()
        binding.rvSms.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvNoSms.visibility = if (hasItems) View.GONE else View.VISIBLE

        if (allSmsNumbers.size > MAX_COLLAPSED_ITEMS && !smsExpanded) {
            // Show collapsed view
            smsAdapter.submitList(allSmsNumbers.take(MAX_COLLAPSED_ITEMS))
            val remaining = allSmsNumbers.size - MAX_COLLAPSED_ITEMS
            binding.tvSmsMore.text = getString(R.string.x_more_available, remaining)
            binding.tvSmsMore.visibility = View.VISIBLE
            binding.tvSmsMore.setOnClickListener {
                smsExpanded = true
                updateSmsDisplay()
            }
        } else {
            // Show all items
            smsAdapter.submitList(allSmsNumbers)
            binding.tvSmsMore.visibility = View.GONE
        }
    }
}
