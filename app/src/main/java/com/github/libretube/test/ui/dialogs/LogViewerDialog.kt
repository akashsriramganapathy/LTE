package com.github.libretube.test.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import android.view.View
import android.widget.ImageButton
import com.github.libretube.test.R
import com.github.libretube.test.helpers.ClipboardHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.libretube.logger.FileLogger
import com.github.libretube.test.extensions.toastFromMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class LogViewerDialog : DialogFragment() {

    private var filterJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 300L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val useRobustLogging = com.github.libretube.test.helpers.PreferenceHelper.getBoolean(com.github.libretube.test.constants.PreferenceKeys.ENABLE_ROBUST_LOGGING, false)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_log_viewer, null)
        val logTextView = view.findViewById<TextView>(R.id.logTextView)
        
        logTextView.text = "Loading logs..."
        var fullLog = ""

        lifecycleScope.launch {
            val logContent = withContext(Dispatchers.IO) {
                if (useRobustLogging) {
                    com.github.libretube.test.util.LogcatRecorder.getLogContent(requireContext())
                } else {
                    FileLogger.getLogContent()
                }
            }
            
            // Truncate to last 2000 lines for performance
            fullLog = withContext(Dispatchers.Default) {
                val lines = logContent.lineSequence().toList()
                if (lines.size > 2000) {
                    lines.takeLast(2000).joinToString("\n")
                } else if (logContent.isNotEmpty()) {
                    logContent
                } else {
                    "Log is empty"
                }
            }
            logTextView.text = fullLog
        }
        
        // Search & Filter Logic
        // Hide search if usage is not robust
        view.findViewById<View>(R.id.searchContainer).visibility = if (useRobustLogging) View.VISIBLE else View.GONE

        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)
        val btnFilterMenu = view.findViewById<ImageButton>(R.id.btnFilterMenu)
        
        fun applyFilter(query: String) {
            filterJob?.cancel()
            
            if (query.isEmpty()) {
                logTextView.text = fullLog
                return
            }

            logTextView.text = "Filtering..."
            
            filterJob = lifecycleScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                
                val filteredText = withContext(Dispatchers.Default) {
                    // Smart filter: If query is "Errors", we search for error tags specifically
                    // Otherwise simple case-insensitive search
                    fullLog.lineSequence().filter { 
                        it.contains(query, ignoreCase = true)
                    }.joinToString("\n")
                }
                
                logTextView.text = if (filteredText.isNotEmpty()) filteredText else "No logs found for \"$query\""
            }
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                applyFilter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        btnFilterMenu.setOnClickListener {
            val popup = android.widget.PopupMenu(requireContext(), it)
            popup.menu.add("Show All")
            popup.menu.add("Show Errors Only")
            popup.menu.add("Show Player Logs")
            popup.menu.add("Show Downloader Logs")
            popup.menu.add("Show Backup Logs")
            popup.menu.add("Show DeArrow Logs")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Show All" -> searchEditText.setText("")
                    "Show Errors Only" -> searchEditText.setText(" E/") // " E/" captures standard logcat errors
                    "Show Player Logs" -> searchEditText.setText("Player")
                    "Show Downloader Logs" -> searchEditText.setText("Downloader")
                    "Show Backup Logs" -> searchEditText.setText("Backup")
                    "Show DeArrow Logs" -> searchEditText.setText("DeArrow")
                }
                true
            }
            popup.show()
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.view_logs)
            .setView(view)
            .create() // Use create() then show() to manage listeners if needed

        dialog.show()

        view.findViewById<Button>(R.id.btnCopy).setOnClickListener {
            // Copy whatever is currently visible
            ClipboardHelper.save(requireContext(), text = logTextView.text.toString(), notify = true)
        }

        view.findViewById<Button>(R.id.btnClear).setOnClickListener {
            if (useRobustLogging) {
                com.github.libretube.test.helpers.PreferenceHelper.putLong(com.github.libretube.test.constants.PreferenceKeys.LOG_VIEWER_START_TIMESTAMP, System.currentTimeMillis())
            } else {
                FileLogger.clearLog()
            }
            requireContext().toastFromMainThread("Logs cleared from view")
            logTextView.text = "Log is empty"
            fullLog = ""
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }
        
        return dialog
    }
}

