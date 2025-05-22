/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package io.github.juby210.acplugins

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import com.lytefast.flexinput.model.Attachment

@AliucordPlugin
@Suppress("unused")
class MoreSlashCommands : Plugin() {
  override fun start(context: Context?) {
    commands.registerCommand(
        "lenny", 
        "Appends ( ͡° ͜ʖ ͡°) to your message.", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The message to append lenny face to"))
    ) { ctx ->
        CommandsAPI.CommandResult(ctx.getString("message")!! + " ( ͡° ͜ʖ ͡°)")
    }

    commands.registerCommand(
        "mock", 
        "Mock a user", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The message to mock"))
    ) { ctx ->
        CommandsAPI.CommandResult(ctx
            .getString("message")!!
            .toCharArray()
            .mapIndexed { i, c -> if (i % 2 == 1) c.uppercaseChar() else c.lowercaseChar() }
            .joinToString(""))
    }

    commands.registerCommand(
        "upper", 
        "Makes text uppercase", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The text to make uppercase"))
    ) { ctx ->
        CommandsAPI.CommandResult(ctx.getString("message")!!.trim().uppercase())
    }

    commands.registerCommand(
        "lower", 
        "Makes text lowercase", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The text to make lowercase"))
    ) { ctx ->
        CommandsAPI.CommandResult(ctx.getString("message")!!.trim().lowercase())
    }

    commands.registerCommand(
        "owo", 
        "Owoify's your text", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The text to owoify"))
    ) { ctx ->
        CommandsAPI.CommandResult(owoify(ctx.getString("message")!!.trim()))
    }

    val displayName = Attachment::class.java.getDeclaredField("displayName").apply { isAccessible = true }
    commands.registerCommand(
        "spoilerfiles", 
        "Marks attachments as spoilers", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "Optional message to send with spoiler files"))
    ) { ctx ->
      for (a in ctx.attachments) displayName[a] = "SPOILER_" + a.displayName
        CommandsAPI.CommandResult(ctx.getString("message") ?: "")
    }

    commands.registerCommand(
        "reverse", 
        "Makes text reversed", 
        listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "message", "The text to reverse"))
    ) { ctx ->
        CommandsAPI.CommandResult(ctx.getString("message")!!.reversed())
    }
  }

  override fun stop(context: Context?) = commands.unregisterAll()

  private fun owoify(text: String): String {
    return text.replace("l", "w").replace("L", "W")
      .replace("r", "w").replace("R", "W")
      .replace("o", "u").replace("O", "U")
  }
}