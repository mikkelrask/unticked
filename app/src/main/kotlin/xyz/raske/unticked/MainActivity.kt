package xyz.raske.unticked

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private lateinit var model: TodoViewModel
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.reconcileReminders() }
    private val open = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let { data ->
            data.data?.let { model.acceptDocument(it, data.flags, diagnostic = false) }
        }
    }
    private val create = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let { data ->
            data.data?.let { model.acceptDocument(it, data.flags, diagnostic = true) }
        }
    }
    private val export = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let(model::exportRecovery)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[TodoViewModel::class.java]
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            SideEffect {
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.isAppearanceLightStatusBars = state.theme == ThemeChoice.LIGHT
                bars.isAppearanceLightNavigationBars = state.theme == ThemeChoice.LIGHT
            }
            TerminalTheme(state.theme) {
                TodoScreen(state, onReminders = { enabled ->
                    Reminders.configure(this, enabled)
                    model.reconcileReminders()
                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }, onNotificationSettings = {
                    startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                }, onOpen = { chooseFile() }, onRefresh = model::refresh, onTheme = model::theme, onDemo = model::showDemo, onDiagnostic = { createDiagnostic() }, onClearRecovery = model::clearRecovery, onComplete = model::complete, onDelete = model::delete, onSwipeAction = model::swipeAction, onEdit = model::beginEdit, onAdd = model::beginAdd, onDraftText = model::updateDraft, onSave = model::saveDraft, onDiscard = model::discardDraft, onUndo = model::undoLast, onAcknowledgeRecovery = model::acknowledgeRecovery, onExportRecovery = {
                    export.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "terminal-todo-recovery.json")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    })
                })
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.refresh() }
    private fun chooseFile() {
        open.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
    private fun createDiagnostic() {
        create.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "terminal-todo-storage-probe.txt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
}
