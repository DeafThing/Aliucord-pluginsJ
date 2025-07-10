/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package io.github.juby210.acplugins

import android.annotation.SuppressLint
import android.content.Context
import android.text.*
import android.text.method.LinkMovementMethod
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

            setActionBarTitle("CustomTimestamps")
            setPadding(0)

            val context = view.context
            val format = settings.getString("format", defaultFormat)
            val useRelativeTime = settings.getBool("useRelativeTime", true)
            val relativeTimeThreshold = settings.getInt("relativeTimeThreshold", 24)

            val guide = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                setPreview(format, this)
                movementMethod = LinkMovementMethod.getInstance()
            }

            // Custom format input
            linearLayout.addView(TextInput(context, "Custom Timestamp Format", format, object : TextWatcher {
                override fun afterTextChanged(s: Editable) = s.toString().let {
                    settings.setString("format", it)
                    setPreview(it, guide)
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            }).apply {
                val padding = DimenUtils.defaultPadding
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(padding, padding, padding, 0) }
            })
            linearLayout.addView(guide)

            // Divider
            linearLayout.addView(Divider(context))

            // Toggle switch for relative time
            val relativeTimeSwitch = Utils.createCheckedSetting(context, CheckedSetting.ViewType.SWITCH, "Use relative time for recent messages", "Recent messages will show '5m ago', older messages use your custom format")
            relativeTimeSwitch.isChecked = useRelativeTime
            linearLayout.addView(relativeTimeSwitch)

            // Threshold input
            val thresholdInput = TextInput(context, "Relative time threshold (hours)", relativeTimeThreshold.toString(), object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    try {
                        val hours = s.toString().toIntOrNull() ?: 24
                        if (hours > 0) {
                            settings.setInt("relativeTimeThreshold", hours)
                        }
                    } catch (e: NumberFormatException) {
                        // Ignore invalid input
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })
            thresholdInput.visibility = if (useRelativeTime) View.VISIBLE else View.GONE
            linearLayout.addView(thresholdInput)

            // Description for threshold
            val thresholdDesc = TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                text = "Messages newer than this threshold will show relative time (e.g., '5 minutes ago'), older messages will use your custom format."
                visibility = if (useRelativeTime) View.VISIBLE else View.GONE
            }
            linearLayout.addView(thresholdDesc)

            // Set up the toggle listener
            relativeTimeSwitch.setOnCheckedListener { isChecked ->
                settings.setBool("useRelativeTime", isChecked)
                thresholdInput.visibility = if (isChecked) View.VISIBLE else View.GONE
                thresholdDesc.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }

        private fun setPreview(formatStr: String, view: TextView) {
            view.text = SpannableStringBuilder("Formatting guide\n\nPreview: ").apply {
                append(format(formatStr, System.currentTimeMillis()))
                setSpan(URLSpan("https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html"), 0, 16, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    override fun start(context: Context?) {
        // Initialize settings reference
        pluginSettings = settings
        
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

    override fun stop(context: Context?) = patcher.unpatchAll()

    companion object {
        const val defaultFormat = "dd.MM.yyyy, HH:mm:ss"
        
        // Thread-safe cache for SimpleDateFormat instances
        private val formatCache = ConcurrentHashMap<String, SimpleDateFormat>()
        
        // Cache for relative time calculations to avoid repeated calculations
        private val relativeTimeCache = ConcurrentHashMap<Long, String>()
        private var lastCacheClear = System.currentTimeMillis()
        
        // Reference to plugin settings
        private lateinit var pluginSettings: SettingsAPI
        
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
                // Use cached relative time if available and still valid (within 1 minute)
                relativeTimeCache[timestamp]?.takeIf { 
                    System.currentTimeMillis() - timestamp < TimeUnit.MINUTES.toMillis(1) 
                } ?: run {
                    val relativeTime = formatRelativeTime(diff)
                    relativeTimeCache[timestamp] = relativeTime
                    relativeTime
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