package com.hisab.app.presentation.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.DialogBackupPasswordBinding
import com.hisab.app.databinding.FragmentBackupBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore screen.
 *  - Save: user picks a location with the system file picker (SAF); nothing is written anywhere else.
 *  - Share: the file goes to a private cache folder and is handed over through a non-exported FileProvider.
 *  - Restore: file is read through SAF, validated, previewed, and only replaced after explicit confirmation.
 */
class BackupFragment : Fragment() {

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackupViewModel by viewModels {
        val ctx = requireContext().applicationContext
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        ViewModelFactory(
            (requireActivity().application as HisabApplication).container,
            appVersion = version,
            safetyCopyWriter = { text -> withContext(Dispatchers.IO) { writeSafetyCopy(ctx.filesDir, text) } }
        )
    }

    private enum class Delivery { SAVE, SHARE }
    private var delivery = Delivery.SAVE
    private var pendingPassword: CharArray? = null
    private var pendingSaveUri: Uri? = null
    private var restoreFileText: String? = null

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { pendingPassword = null; return@registerForActivityResult }
        pendingSaveUri = uri
        viewModel.createBackup(pendingPassword, fileNameOf(uri))
        pendingPassword = null
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
            }
            if (text == null) viewModel.reportReadError() else { restoreFileText = text; viewModel.inspect(text, null) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveFileButton.setOnClickListener { delivery = Delivery.SAVE; askNewPassword() }
        binding.shareButton.setOnClickListener { delivery = Delivery.SHARE; askNewPassword() }
        binding.restoreButton.setOnClickListener { openDocument.launch(arrayOf("*/*")) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { s ->
                        val working = s == BackupUiState.Working
                        binding.backupProgress.visibility = if (working) View.VISIBLE else View.INVISIBLE
                        binding.saveFileButton.isEnabled = !working
                        binding.shareButton.isEnabled = !working
                        binding.restoreButton.isEnabled = !working
                    }
                }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    private fun handle(e: BackupEvent) {
        when (e) {
            is BackupEvent.BackupReady -> deliver(e)
            is BackupEvent.PasswordRequired -> askExistingPassword(e.wrong)
            is BackupEvent.RestorePreview -> showPreview(e)
            is BackupEvent.Restored -> { restoreFileText = null; snack(getString(R.string.backup_restored_message)) }
            is BackupEvent.Failure -> snack(e.message ?: getString(e.messageRes))
        }
    }

    // ---- creating ---------------------------------------------------------------------------

    private fun askNewPassword() {
        val d = DialogBackupPasswordBinding.inflate(layoutInflater)
        d.passwordMessage.setText(R.string.backup_password_message)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_password_title)
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_continue, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = d.passwordInput.text?.toString().orEmpty()
                val p2 = d.confirmInput.text?.toString().orEmpty()
                if (p1 != p2) { d.confirmLayout.error = getString(R.string.backup_password_mismatch); return@setOnClickListener }
                pendingPassword = p1.takeIf { it.isNotEmpty() }?.toCharArray()
                dialog.dismiss()
                if (delivery == Delivery.SAVE) createDocument.launch(defaultFileName())
                else viewModel.createBackup(pendingPassword.also { pendingPassword = null }, defaultFileName())
            }
        }
        dialog.show()
    }

    private fun deliver(e: BackupEvent.BackupReady) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            if (delivery == Delivery.SAVE) {
                val uri = pendingSaveUri ?: return@launch
                pendingSaveUri = null
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        // "wt" truncates: an existing file picked in the dialog is replaced, never half-overwritten.
                        ctx.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(e.text) } != null
                    }.getOrDefault(false)
                }
                snack(getString(if (ok) R.string.backup_created_message else R.string.backup_error_write))
            } else {
                val uri = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(ctx.cacheDir, "shared_backups").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                        val file = File(dir, e.fileName).apply { writeText(e.text, Charsets.UTF_8) }
                        FileProvider.getUriForFile(ctx, "${ctx.packageName}.backupprovider", file)
                    }.getOrNull()
                }
                if (uri == null) { snack(getString(R.string.backup_error_write)); return@launch }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.backup_file_share_title)))
            }
        }
    }

    // ---- restoring --------------------------------------------------------------------------

    private fun askExistingPassword(wrong: Boolean) {
        val d = DialogBackupPasswordBinding.inflate(layoutInflater)
        d.passwordMessage.setText(if (wrong) R.string.backup_password_wrong else R.string.backup_password_ask_message)
        d.confirmLayout.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_password_ask_title)
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel) { _, _ -> restoreFileText = null }
            .setPositiveButton(R.string.action_continue) { _, _ ->
                val text = restoreFileText ?: return@setPositiveButton
                viewModel.inspect(text, d.passwordInput.text?.toString().orEmpty().toCharArray())
            }
            .show()
    }

    private fun showPreview(e: BackupEvent.RestorePreview) {
        val h = e.parsed.header
        val created = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(h.createdAtMillis))
        val warnings = if (e.warnings.isEmpty()) "" else getString(R.string.backup_preview_warnings, e.warnings.take(5).joinToString("\n"))
        val message = getString(
            R.string.backup_preview_message, created, h.schemaVersion.toInt(),
            countsText(e.parsed.data.counts()), countsText(e.currentCounts), warnings
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_preview_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> viewModel.cancelRestore(); restoreFileText = null }
            .setOnCancelListener { viewModel.cancelRestore(); restoreFileText = null }
            .setPositiveButton(R.string.backup_action_restore) { _, _ -> viewModel.confirmRestore() }
            .show()
    }

    private fun countsText(counts: Map<String, Int>): String {
        val labels = linkedMapOf(
            "accounts" to R.string.backup_table_accounts, "categories" to R.string.backup_table_categories,
            "people" to R.string.backup_table_people, "transactions" to R.string.backup_table_transactions,
            "budgets" to R.string.backup_table_budgets, "goals" to R.string.backup_table_goals,
            "goal_contributions" to R.string.backup_table_goal_contributions,
            "recurring_transactions" to R.string.backup_table_recurring_transactions
        )
        return labels.entries.joinToString("\n") { (k, res) -> getString(R.string.backup_count_line, getString(res), counts[k] ?: 0) }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun defaultFileName(): String =
        "hisab-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".hisab"

    private fun fileNameOf(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: defaultFileName()

    private fun snack(text: String) = Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Keeps the three newest pre-restore copies inside the app's private storage. */
        fun writeSafetyCopy(filesDir: File, text: String) {
            val dir = File(filesDir, "safety_copies").apply { mkdirs() }
            File(dir, "before-restore-${System.currentTimeMillis()}.hisab").writeText(text, Charsets.UTF_8)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        }
    }
}
