package com.github.libretube.test.ui.preferences

import android.os.Bundle
import android.util.AttributeSet
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.test.R
import com.github.libretube.test.databinding.FragmentSettingsSearchBinding
import com.github.libretube.test.ui.activities.SettingsActivity
import com.github.libretube.test.ui.extensions.onSystemInsets
import com.github.libretube.test.extensions.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class SettingsSearchFragment : Fragment() {

    private var _binding: FragmentSettingsSearchBinding? = null
    private val binding get() = _binding!!

    private val allSuggestions = mutableListOf<Suggestion>()
    private val adapter = SearchAdapter()
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the main activity toolbar
        (activity as? SettingsActivity)?.binding?.toolbar?.visibility = View.GONE

        // Handle system insets (Status Bar)
        binding.searchBarContainer.onSystemInsets { view, insets ->
            val params = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            params?.topMargin = insets.top + 8f.dpToPx()
            view.layoutParams = params
        }

        binding.searchResults.adapter = adapter

        binding.btnBack.setOnClickListener {
             parentFragmentManager.popBackStack()
        }

        binding.btnClear.setOnClickListener {
             binding.searchEditText.text.clear()
        }
        
        binding.searchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            
            searchJob?.cancel()
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                if (query.isBlank()) {
                    adapter.submitList(emptyList())
                } else {
                    val filtered = withContext(Dispatchers.Default) {
                        allSuggestions.mapNotNull { suggestion ->
                            val titleScore = when {
                                suggestion.title.equals(query, ignoreCase = true) -> 100
                                suggestion.title.startsWith(query, ignoreCase = true) -> 80
                                suggestion.title.contains(query, ignoreCase = true) -> 50
                                else -> 0
                            }
                            
                            val summaryScore = if (suggestion.summary.contains(query, ignoreCase = true)) 20 else 0
                            
                            val totalScore = titleScore + summaryScore
                            if (totalScore > 0) {
                                suggestion.copy(score = totalScore)
                            } else {
                                null
                            }
                        }.sortedByDescending { it.score }
                    }
                    adapter.submitList(filtered)
                }
            }
        }
        
        // request focus
        binding.searchEditText.requestFocus()
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

        // Index in background
        CoroutineScope(Dispatchers.Default).launch {
            indexPreferences()
        }
    }

    private fun indexPreferences() {
        allSuggestions.clear()
        
        val mappings = listOf(
            Triple(R.xml.settings, null, R.string.settings),
            Triple(R.xml.appearance_settings, AppearanceSettings::class.java, R.string.appearance),

            Triple(R.xml.import_export_settings, BackupRestoreSettings::class.java, R.string.backup_restore),
            Triple(R.xml.general_settings, GeneralSettings::class.java, R.string.general),
            Triple(R.xml.history_settings, HistorySettings::class.java, R.string.history),
            Triple(R.xml.notification_settings, NotificationSettings::class.java, R.string.notifications),
            Triple(R.xml.player_settings, PlayerSettings::class.java, R.string.player),
            Triple(R.xml.content_settings, ContentSettings::class.java, R.string.content_settings)
        )

        for ((xmlRes, fragmentClass, titleRes) in mappings) {
            val breadcrumb = getString(titleRes)
            parseXml(xmlRes, fragmentClass, breadcrumb)
        }
    }

    private fun parseXml(xmlRes: Int, fragmentClass: Class<out Fragment>?, breadcrumb: String) {
        val parser = resources.getXml(xmlRes)
        val attrs = Xml.asAttributeSet(parser)
        
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name
                    // Check if it's a preference tag
                    if (name.contains("Preference") || name == "SwitchPreferenceCompat") {
                        val key = getAttributeStringValue(attrs, "key")
                        val title = getAttributeStringValue(attrs, "title")
                        val summary = getAttributeStringValue(attrs, "summary")
                        val fragment = getAttributeStringValue(attrs, "fragment")

                        if (!key.isNullOrBlank() && !title.isNullOrBlank()) {
                             allSuggestions.add(
                                 Suggestion(
                                     title = title,
                                     summary = summary.orEmpty(),
                                     key = key,
                                     fragmentClass = fragmentClass,
                                     fragmentClassName = fragment,
                                     breadcrumb = breadcrumb
                                 )
                             )
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAttributeStringValue(attrs: AttributeSet, attributeName: String): String? {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val appNamespace = "http://schemas.android.com/apk/res-auto"

        // Try Android namespace first
        var resId = attrs.getAttributeResourceValue(androidNamespace, attributeName, 0)
        if (resId == 0) {
            // Try App namespace
            resId = attrs.getAttributeResourceValue(appNamespace, attributeName, 0)
        }

        if (resId != 0) {
            return try {
                getString(resId)
            } catch (e: Exception) {
                null
            }
        }

        // Try getting raw value from Android namespace
        var value = attrs.getAttributeValue(androidNamespace, attributeName)
        if (value == null) {
            // Try App namespace
            value = attrs.getAttributeValue(appNamespace, attributeName)
        }
        return value
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore the main activity toolbar
        (activity as? SettingsActivity)?.binding?.toolbar?.visibility = View.VISIBLE
        _binding = null
    }

    data class Suggestion(
        val title: String,
        val summary: String,
        val key: String,
        val fragmentClass: Class<out Fragment>?,
        val fragmentClassName: String? = null,
        val breadcrumb: String,
        var score: Int = 0
    )

    inner class SearchAdapter : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {
        private var items = listOf<Suggestion>()

        fun submitList(newItems: List<Suggestion>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_settings_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.settingTitle)
            private val summary: TextView = itemView.findViewById(R.id.settingSummary)
            private val breadcrumb: TextView = itemView.findViewById(R.id.settingBreadcrumb)

            fun bind(item: Suggestion) {
                title.text = item.title
                summary.text = item.summary
                breadcrumb.text = item.breadcrumb
                if (item.summary.isBlank()) summary.visibility = View.GONE else summary.visibility = View.VISIBLE

                itemView.setOnClickListener {
                    navigateToSetting(item)
                }
            }
        }
    }

    private fun navigateToSetting(item: Suggestion) {
         val targetFragmentClass = item.fragmentClass ?: item.fragmentClassName?.let {
             try {
                 Class.forName(it) as? Class<out Fragment>
             } catch (e: Exception) {
                 null
             }
         }

         if (targetFragmentClass == null) return

         val fragment = targetFragmentClass.newInstance().apply {
            arguments = bundleOf("highlight_key" to item.key)
         }
        
        parentFragmentManager.commit {
             replace(R.id.settings, fragment)
             addToBackStack(null)
        }
    }
}
