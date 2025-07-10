/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package io.github.juby210.acplugins

import android.annotation.SuppressLint
import android.content.Context
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Main
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.fragments.SettingsPage
import com.aliucord.patcher.Hook
import com.aliucord.utils.DimenUtils
import com.aliucord.views.TextInput
import com.aliucord.views.Divider
import com.aliucord.Utils
import com.discord.views.CheckedSetting
import com.discord.utilities.time.Clock
import com.discord.utilities.time.TimeUtils
import com.lytefast.flexinput.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.lang.ref.WeakReference
import androidx.recyclerview.widget.RecyclerView

@AliucordPlugin
@Suppress("unused")
@SuppressLint("SimpleDateFormat")
class CustomTimestamps : Plugin() {
    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    class PluginSettings(val settings: SettingsAPI) : SettingsPage() {
        override fun onViewBound(view: View) {
            super.onViewBound(view)

            setActionBarTitle("Custom Timestamps")
            setPadding(0)

            val context = view.context
            val format = settings.getString("format", defaultFormat)
            val useRelativeTime = settings.getBool("useRelativeTime", true)
            val relativeTimeThreshold = settings.getInt("relativeTimeThreshold", 24)

            val padding = DimenUtils.defaultPadding
            val halfPadding = padding / 2

            // Header section
            addHeaderSection(context, padding)

            // Format input section
            addFormatSection(context, format, padding, halfPadding)

            // Divider
            linearLayout.addView(Divider(context))

            // Relative time section
            addRelativeTimeSection(context, useRelativeTime, relativeTimeThreshold, padding, halfPadding)

            // Footer section
            addFooterSection(context, padding)
        }

        private fun addHeaderSection(context: Context, padding: Int) {
            val headerText = TextView(context, null, 0, R.i.UiKit_Settings_Item_Header).apply {
                text = "Timestamp Configuration"
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, padding, padding, padding / 2)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(headerText)

            val descText = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = "Customize how timestamps appear in Discord messages. You can use a custom format for all messages, or combine relative time for recent messages with custom formatting for older ones."
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, padding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(descText)
        }

        private fun addFormatSection(context: Context, format: String, padding: Int, halfPadding: Int) {
            // Section header
            val formatHeader = TextView(context, null, 0, R.i.UiKit_Settings_Item_Label).apply {
                text = "Custom Format"
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, padding, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(formatHeader)

            // Format input
            val formatInput = TextInput(context, "Enter your custom timestamp format", format, object : TextWatcher {
                override fun afterTextChanged(s: Editable) = s.toString().let {
                    settings.setString("format", it)
                    updatePreview(it)
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            }).apply {
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(formatInput)

            // Preview display
            val previewText = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                setPreview(format, this)
                movementMethod = LinkMovementMethod.getInstance()
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(previewText)

            // Format examples
            val examplesText = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = buildString {
                    append("Examples:\n")
                    append("• dd.MM.yyyy, HH:mm:ss → ${format("dd.MM.yyyy, HH:mm:ss", System.currentTimeMillis())}\n")
                    append("• MMM dd, yyyy 'at' h:mm a → ${format("MMM dd, yyyy 'at' h:mm a", System.currentTimeMillis())}\n")
                    append("• E, MMM dd HH:mm → ${format("E, MMM dd HH:mm", System.currentTimeMillis())}")
                }
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, padding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(examplesText)

            // Store reference for updating preview
            formatInput.tag = previewText
        }

        private fun updatePreview(formatStr: String) {
            // Find the preview text view through the format input's tag
            linearLayout.children.forEach { child ->
                if (child is TextInput && child.tag is TextView) {
                    setPreview(formatStr, child.tag as TextView)
                }
            }
        }

        private fun addRelativeTimeSection(context: Context, useRelativeTime: Boolean, relativeTimeThreshold: Int, padding: Int, halfPadding: Int) {
            // Section header
            val relativeHeader = TextView(context, null, 0, R.i.UiKit_Settings_Item_Label).apply {
                text = "Relative Time Settings"
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, padding, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(relativeHeader)

            // Toggle switch for relative time
            val relativeTimeSwitch = Utils.createCheckedSetting(
                context,
                CheckedSetting.ViewType.SWITCH,
                "Enable relative time",
                "Show recent messages as '5m ago', '2h ago', etc."
            ).apply {
                isChecked = useRelativeTime
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(relativeTimeSwitch)

            // Real-time update toggle
            val realTimeUpdateSwitch = Utils.createCheckedSetting(
                context,
                CheckedSetting.ViewType.SWITCH,
                "Real-time timestamp updates",
                "Automatically update relative timestamps (e.g., '5m ago' → '6m ago')"
            ).apply {
                isChecked = settings.getBool("realTimeUpdates", true)
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
                visibility = if (useRelativeTime) View.VISIBLE else View.GONE
            }
            linearLayout.addView(realTimeUpdateSwitch)

            realTimeUpdateSwitch.setOnCheckedListener { isChecked ->
                settings.setBool("realTimeUpdates", isChecked)
                if (isChecked) {
                    startTimestampUpdater()
                } else {
                    stopTimestampUpdater()
                }
            }

            // Threshold input container
            val thresholdContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (useRelativeTime) View.VISIBLE else View.GONE
            }

            // Threshold label
            val thresholdLabel = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = "Relative time threshold (hours)"
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, halfPadding, padding, halfPadding / 2)
                this.layoutParams = layoutParams
            }
            thresholdContainer.addView(thresholdLabel)

            // Threshold input
            val thresholdInput = TextInput(context, "Hours", relativeTimeThreshold.toString(), object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    try {
                        val hours = s.toString().toIntOrNull() ?: 24
                        if (hours > 0) {
                            settings.setInt("relativeTimeThreshold", hours)
                            updateThresholdDescription(hours)
                        }
                    } catch (e: NumberFormatException) {
                        // Ignore invalid input
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            }).apply {
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            thresholdContainer.addView(thresholdInput)

            // Description for threshold
            val thresholdDesc = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = getThresholdDescription(relativeTimeThreshold)
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, 0, padding, halfPadding)
                this.layoutParams = layoutParams
            }
            thresholdContainer.addView(thresholdDesc)

            linearLayout.addView(thresholdContainer)

            // Store reference for updating threshold description
            thresholdInput.tag = thresholdDesc

            // Set up the toggle listener
            relativeTimeSwitch.setOnCheckedListener { isChecked ->
                settings.setBool("useRelativeTime", isChecked)
                thresholdContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                realTimeUpdateSwitch.visibility = if (isChecked) View.VISIBLE else View.GONE
                
                if (isChecked && settings.getBool("realTimeUpdates", true)) {
                    startTimestampUpdater()
                } else {
                    stopTimestampUpdater()
                }
            }
        }

        private fun updateThresholdDescription(hours: Int) {
            // Find the threshold description through the threshold input's tag
            linearLayout.children.forEach { child ->
                if (child is LinearLayout) {
                    child.children.forEach { subChild ->
                        if (subChild is TextInput && subChild.tag is TextView) {
                            (subChild.tag as TextView).text = getThresholdDescription(hours)
                        }
                    }
                }
            }
        }

        private fun getThresholdDescription(hours: Int): String {
            return "Messages newer than $hours ${if (hours == 1) "hour" else "hours"} will show relative time (e.g., '5 minutes ago'), older messages will use your custom format."
        }

        private fun addFooterSection(context: Context, padding: Int) {
            val footerText = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = "💡 Tip: Changes apply immediately to new messages. Scroll up to see the effect on older messages."
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(padding, padding, padding, padding)
                this.layoutParams = layoutParams
            }
            linearLayout.addView(footerText)
        }

        private fun setPreview(formatStr: String, view: TextView) {
            val currentTime = System.currentTimeMillis()
            val formattedTime = format(formatStr, currentTime)
            
            view.text = SpannableStringBuilder().apply {
                append("Preview: ")
                val start = length
                append(formattedTime)
                val end = length
                
                // Make the formatted time bold
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                append("\n\nFor more formatting options, see the ")
                val linkStart = length
                append("Java SimpleDateFormat documentation")
                val linkEnd = length
                
                setSpan(URLSpan("https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html"), 
                       linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Extension function to iterate over LinearLayout children
        private val LinearLayout.children: Sequence<View>
            get() = (0 until childCount).asSequence().map { getChildAt(it) }
    }

    override fun start(context: Context?) {
        // Initialize settings reference
        pluginSettings = settings
        
        // Start the timestamp updater if enabled
        if (settings.getBool("useRelativeTime", true) && settings.getBool("realTimeUpdates", true)) {
            startTimestampUpdater()
        }
        
        patcher.patch(
            TimeUtils::class.java.getDeclaredMethod("toReadableTimeString", Context::class.java, Long::class.javaPrimitiveType, Clock::class.java),
            Hook { 
                val timestamp = it.args[1] as Long
                val ctx = it.args[0] as Context
                
                it.result = if (settings.getBool("useRelativeTime", true)) {
                    formatWithRelativeTime(ctx, timestamp)
                } else {
                    format(settings.getString("format", defaultFormat), timestamp)
                }
            }
        )
    }

    override fun stop(context: Context?) {
        stopTimestampUpdater()
        patcher.unpatchAll()
    }

    companion object {
        const val defaultFormat = "dd.MM.yyyy, HH:mm:ss"
        
        // Thread-safe cache for SimpleDateFormat instances
        private val formatCache = ConcurrentHashMap<String, SimpleDateFormat>()
        
        // Cache for relative time calculations to avoid repeated calculations
        private val relativeTimeCache = ConcurrentHashMap<Long, String>()
        private var lastCacheClear = System.currentTimeMillis()
        
        // Reference to plugin settings
        private lateinit var pluginSettings: SettingsAPI
        
        // Real-time update system
        private var timestampUpdater: ScheduledExecutorService? = null
        private var updateTask: ScheduledFuture<*>? = null
        private val activeRecyclerViews = mutableSetOf<WeakReference<RecyclerView>>()
        
        fun startTimestampUpdater() {
            // Don't start if already running
            if (timestampUpdater != null && !timestampUpdater!!.isShutdown) return
            
            timestampUpdater = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "TimestampUpdater").apply {
                    isDaemon = true
                }
            }
            
            updateTask = timestampUpdater?.scheduleWithFixedDelay({
                try {
                    updateVisibleTimestamps()
                } catch (e: Exception) {
                    Main.logger.error("Error updating timestamps", e)
                }
            }, 60, 60, TimeUnit.SECONDS) // Update every minute
        }
        
        fun stopTimestampUpdater() {
            updateTask?.cancel(true)
            updateTask = null
            
            timestampUpdater?.shutdown()
            timestampUpdater = null
            
            // Clear the recycler view references
            activeRecyclerViews.clear()
        }
        
        private fun updateVisibleTimestamps() {
            // Clear expired weak references
            activeRecyclerViews.removeIf { it.get() == null }
            
            // Update visible items in active RecyclerViews
            activeRecyclerViews.forEach { ref ->
                ref.get()?.let { recyclerView ->
                    try {
                        recyclerView.post {
                            // Clear the cache to force recalculation
                            relativeTimeCache.clear()
                            
                            // Notify adapter of potential changes
                            recyclerView.adapter?.notifyDataSetChanged()
                        }
                    } catch (e: Exception) {
                        Main.logger.error("Error updating RecyclerView", e)
                    }
                }
            }
        }
        
        fun registerRecyclerView(recyclerView: RecyclerView) {
            activeRecyclerViews.add(WeakReference(recyclerView))
        }
        
        fun format(format: String?, time: Long): String {
            val formatStr = format ?: defaultFormat
            return try {
                val formatter = formatCache.getOrPut(formatStr) {
                    SimpleDateFormat(formatStr, Locale.getDefault())
                }
                // SimpleDateFormat is not thread-safe, so we need to synchronize access
                synchronized(formatter) {
                    formatter.format(Date(time))
                }
            } catch (e: Throwable) {
                Main.logger.info("Invalid format for CustomTimestamps, using default format: $e")
                val defaultFormatter = formatCache.getOrPut(defaultFormat) {
                    SimpleDateFormat(defaultFormat, Locale.getDefault())
                }
                synchronized(defaultFormatter) {
                    defaultFormatter.format(Date(time))
                }
            }
        }
        
        private fun formatWithRelativeTime(context: Context, timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            // Clear cache periodically to prevent memory leaks
            if (now - lastCacheClear > TimeUnit.HOURS.toMillis(1)) {
                relativeTimeCache.clear()
                lastCacheClear = now
            }
            
            // Get threshold from settings (default 24 hours)
            val thresholdHours = pluginSettings.getInt("relativeTimeThreshold", 24)
            val thresholdMillis = TimeUnit.HOURS.toMillis(thresholdHours.toLong())
            
            return if (diff < thresholdMillis && diff >= 0) {
                // For real-time updates, don't cache very recent messages
                if (pluginSettings.getBool("realTimeUpdates", true) && diff < TimeUnit.HOURS.toMillis(1)) {
                    formatRelativeTime(diff)
                } else {
                    // Use cached relative time if available and still valid
                    relativeTimeCache[timestamp] ?: run {
                        val relativeTime = formatRelativeTime(diff)
                        relativeTimeCache[timestamp] = relativeTime
                        relativeTime
                    }
                }
            } else {
                // Use custom format for older messages
                val customFormat = pluginSettings.getString("format", defaultFormat)
                format(customFormat, timestamp)
            }
        }
        
        private fun formatRelativeTime(diffMillis: Long): String {
            val seconds = diffMillis / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> "${days / 7}w ago"
            }
        }
    }
}