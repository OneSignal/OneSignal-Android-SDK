package com.onesignal.sdktest.ui.main

import android.content.Intent
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
import com.onesignal.sdktest.ui.adapter.NotificationGridAdapter
import com.onesignal.sdktest.ui.adapter.PairListAdapter
import com.onesignal.sdktest.ui.adapter.SingleListAdapter
import com.onesignal.sdktest.ui.secondary.SecondaryActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Adapters
    private lateinit var aliasesAdapter: PairListAdapter
    private lateinit var emailsAdapter: SingleListAdapter
    private lateinit var smsAdapter: SingleListAdapter
    private lateinit var tagsAdapter: PairListAdapter
    private lateinit var triggersAdapter: PairListAdapter
    private lateinit var notificationsAdapter: NotificationGridAdapter
    private lateinit var inAppMessagesAdapter: InAppMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupAdapters()
        setupRecyclerViews()
        setupClickListeners()
        setupObservers()
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

        notificationsAdapter = NotificationGridAdapter(NotificationType.values().toList()) { type ->
            viewModel.sendNotification(type)
        }

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

        // Notifications Grid (2 columns)
        binding.rvNotifications.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = notificationsAdapter
        }

        // In-App Messages Grid (2 columns)
        binding.rvInAppMessages.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = inAppMessagesAdapter
        }
    }

    private fun setupClickListeners() {
        // Consent buttons
        binding.btnAllowConsent.setOnClickListener {
            viewModel.setPrivacyConsent(true)
        }

        binding.btnRevokeConsent.setOnClickListener {
            viewModel.revokeConsent()
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
            binding.privacyConsentLayout.visibility = if (granted) View.GONE else View.VISIBLE
            binding.nestedScrollView.visibility = if (granted) View.VISIBLE else View.GONE
        }

        // Aliases
        viewModel.aliases.observe(this) { aliases ->
            aliasesAdapter.submitList(aliases)
            binding.rvAliases.visibility = if (aliases.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoAliases.visibility = if (aliases.isEmpty()) View.VISIBLE else View.GONE
        }

        // Emails
        viewModel.emails.observe(this) { emails ->
            emailsAdapter.submitList(emails)
            binding.rvEmails.visibility = if (emails.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoEmails.visibility = if (emails.isEmpty()) View.VISIBLE else View.GONE
        }

        // SMS
        viewModel.smsNumbers.observe(this) { smsNumbers ->
            smsAdapter.submitList(smsNumbers)
            binding.rvSms.visibility = if (smsNumbers.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoSms.visibility = if (smsNumbers.isEmpty()) View.VISIBLE else View.GONE
        }

        // Tags
        viewModel.tags.observe(this) { tags ->
            tagsAdapter.submitList(tags)
            binding.rvTags.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoTags.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
        }

        // Triggers
        viewModel.triggers.observe(this) { triggers ->
            triggersAdapter.submitList(triggers)
            binding.rvTriggers.visibility = if (triggers.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoTriggers.visibility = if (triggers.isEmpty()) View.VISIBLE else View.GONE
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
}
