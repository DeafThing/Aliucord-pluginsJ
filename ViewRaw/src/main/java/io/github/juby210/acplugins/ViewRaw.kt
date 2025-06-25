/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package io.github.juby210.acplugins

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.Constants
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.fragments.SettingsPage
import com.aliucord.patcher.Hook
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.utils.GsonUtils
import com.aliucord.utils.MDUtils
import com.aliucord.views.Button
import com.aliucord.views.Divider
import com.discord.databinding.WidgetChatListActionsBinding
import com.discord.models.message.Message
import com.discord.models.user.CoreUser
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.lytefast.flexinput.R
import java.util.regex.Pattern

@AliucordPlugin
@Suppress("unused")
@SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
class ViewRaw : Plugin() {
    init {
        needsResources = true
    }

    class Page(private val message: Message) : SettingsPage() {
        override fun onViewBound(view: View) {
            super.onViewBound(view)

            setActionBarTitle("Raw message by " + CoreUser(message.author).username)
            setActionBarSubtitle("View Raw")

            val context = view.context
            val layout = linearLayout

            val content = message.content
            val markdownContent = processMarkdown(content)

            val icon = ContextCompat.getDrawable(context, R.e.ic_copy_24dp)?.apply {
                mutate()
                setTint(ColorCompat.getThemedColor(context, R.b.colorInteractiveNormal))
            }

            // First row of buttons
            layout.addView(LinearLayout(context).apply {
                setPadding(0, 0, 0, 12.dp)
                addView(Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        weight = 1f
                        marginEnd = 3.dp
                    }
                    text = "Copy Raw"
                    contentDescription = "Copy Raw Content"
                    isEnabled = !content.isNullOrEmpty()
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    setOnClickListener {
                        Utils.setClipboard("Copy Raw Content", content)
                        Utils.showToast("Copied raw content to clipboard!")
                    }
                })
                addView(Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        weight = 1f
                        marginStart = 3.dp
                    }
                    text = "Copy Data"
                    contentDescription = "Copy Raw Data"
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    setOnClickListener {
                        Utils.setClipboard("Copy Data", GsonUtils.toJsonPretty(message))
                        Utils.showToast("Copied data to clipboard!")
                    }
                })
            })

            // Second row - Copy Markdown button
            layout.addView(LinearLayout(context).apply {
                setPadding(0, 0, 0, 12.dp)
                addView(Button(context).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        weight = 1f
                    }
                    text = "Copy Markdown"
                    contentDescription = "Copy with Markdown"
                    isEnabled = !content.isNullOrEmpty()
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    setOnClickListener {
                        Utils.setClipboard("Copy Markdown", markdownContent)
                        Utils.showToast("Copied markdown content to clipboard!")
                    }
                })
            })

            // Display raw content
            if (!content.isNullOrEmpty()) {
                layout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Header).apply {
                    text = "Raw Content"
                    typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
                    setPadding(0, paddingTop, paddingRight, paddingBottom)
                })
                layout.addView(TextView(context).apply {
                    text = MDUtils.renderCodeBlock(context, SpannableStringBuilder(), null, content)
                    setTextIsSelectable(true)
                })
                layout.addView(Divider(context))

                // Display markdown content if different from raw
                if (markdownContent != content) {
                    layout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Header).apply {
                        text = "Markdown Content"
                        typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
                        setPadding(0, paddingTop, paddingRight, paddingBottom)
                    })
                    layout.addView(TextView(context).apply {
                        text = MDUtils.renderCodeBlock(context, SpannableStringBuilder(), null, markdownContent)
                        setTextIsSelectable(true)
                    })
                    layout.addView(Divider(context))
                }
            }

            layout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Header).apply {
                text = "All Raw Data"
                typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
                setPadding(0, paddingTop, paddingRight, paddingBottom)
            })
            layout.addView(TextView(context).apply {
                text = MDUtils.renderCodeBlock(context, SpannableStringBuilder(), "js", GsonUtils.toJsonPretty(message))
                setTextIsSelectable(true)
            })
        }

        private fun processMarkdown(content: String?): String {
            if (content == null) return ""
            
            // Convert custom emojis from <a?:name:id> to :name:
            val pattern = Pattern.compile("<a?:([a-zA-Z0-9_]+):\\d+>")
            val matcher = pattern.matcher(content)
            val sb = StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(sb, ":" + matcher.group(1) + ":")
            }
            matcher.appendTail(sb)
            return sb.toString()
        }
    }

    override fun start(ctx: Context) {
        val icon = ResourcesCompat.getDrawable(
            resources,
            resources.getIdentifier("ic_viewraw", "drawable", "io.github.juby210.acplugins"), null
        ) ?: ctx.resources.getDrawable(R.e.design_password_eye, null).mutate()

        val copyRawId = View.generateViewId()
        val viewRawId = View.generateViewId()
        val viewDataId = View.generateViewId()

        val c = WidgetChatListActions::class.java
        val getBinding = c.getDeclaredMethod("getBinding").apply { isAccessible = true }

        patcher.patch(c.getDeclaredMethod("configureUI", WidgetChatListActions.Model::class.java), Hook {
            val binding = getBinding.invoke(it.thisObject) as WidgetChatListActionsBinding
            val message = (it.args[0] as WidgetChatListActions.Model).message
            
            // Copy Raw button functionality
            val copyRaw = binding.root.findViewById<TextView>(copyRawId)
            copyRaw.setOnClickListener { _ ->
                Utils.setClipboard("Copy Raw", message.content)
                Utils.showToast("Copied content to clipboard!")
                (it.thisObject as WidgetChatListActions).dismiss()
            }
            
            // View Raw button functionality
            val viewRaw = binding.root.findViewById<TextView>(viewRawId)
            viewRaw.setOnClickListener { e ->
                Utils.openPageWithProxy(e.context, Page(message))
                (it.thisObject as WidgetChatListActions).dismiss()
            }

            // View Data button functionality
            val viewData = binding.root.findViewById<TextView>(viewDataId)
            viewData.setOnClickListener { e ->
                Utils.openPageWithProxy(e.context, Page(message))
                (it.thisObject as WidgetChatListActions).dismiss()
            }
        })

        patcher.patch(c, "onViewCreated", arrayOf(View::class.java, Bundle::class.java), Hook {
            val linearLayout = (it.args[0] as NestedScrollView).getChildAt(0) as LinearLayout
            val context = linearLayout.context

            // Copy Raw button
            linearLayout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                id = copyRawId
                text = "Copy Raw"
                context.getDrawable(R.e.ic_copy_24dp)?.run {
                    mutate()
                    setTint(ColorCompat.getThemedColor(context, R.b.colorInteractiveNormal))
                    setCompoundDrawablesRelativeWithIntrinsicBounds(this, null, null, null)
                }
            })

            // View Raw button
            icon.setTint(ColorCompat.getThemedColor(context, R.b.colorInteractiveNormal))
            linearLayout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                id = viewRawId
                text = "View Raw"
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            })

            // View Data button (third button)
            val dataIcon = context.getDrawable(R.e.ic_file_text_24dp)?.apply {
                mutate()
                setTint(ColorCompat.getThemedColor(context, R.b.colorInteractiveNormal))
            }
            linearLayout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                id = viewDataId
                text = "View Data"
                setCompoundDrawablesRelativeWithIntrinsicBounds(dataIcon, null, null, null)
            })
        })
    }

    override fun stop(context: Context?) = patcher.unpatchAll()
}