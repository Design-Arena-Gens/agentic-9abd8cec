package com.max.pandaai.intent

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.max.pandaai.R
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// A lightweight result wrapper so the UI knows if a command was executed.
data class CommandResult(
    val handled: Boolean,
    val message: String? = null
)

// Parses natural language commands and triggers Android intents.
class IntentHandler(private val context: Context) {

    private val packageManager = context.packageManager

    fun handleCommand(raw: String): CommandResult {
        val lower = raw.lowercase(Locale.getDefault()).trim()
        return when {
            lower.startsWith("open ") -> openApp(lower.removePrefix("open ").trim())
            lower.startsWith("search on google for ") -> searchWeb(lower.removePrefix("search on google for ").trim())
            lower.startsWith("search for ") -> searchWeb(lower.removePrefix("search for ").trim())
            lower.startsWith("call ") -> dialNumber(lower.removePrefix("call ").trim())
            lower.startsWith("send sms") || lower.startsWith("send text") -> sendSms(lower)
            lower.contains("add calendar event") || lower.startsWith("create event") -> addCalendarEvent(raw)
            lower.contains("open camera") || lower.startsWith("camera") -> openCamera()
            lower.contains("play music") -> playMusic()
            lower.contains("set alarm") || lower.startsWith("wake me") -> setAlarm(raw)
            lower.contains("time is it") || lower.contains("current time") -> tellTime()
            lower.contains("date is it") || lower.contains("today's date") -> tellDate()
            else -> CommandResult(false)
        }
    }

    private fun openApp(target: String): CommandResult {
        val packageName = when {
            target.contains("youtube") -> "com.google.android.youtube"
            target.contains("whatsapp") -> "com.whatsapp"
            target.contains("instagram") -> "com.instagram.android"
            else -> null
        }

        if (packageName != null) {
            return launchPackage(packageName)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(target)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            CommandResult(true)
        } else {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun launchPackage(packageName: String): CommandResult {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            CommandResult(true)
        } else {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun searchWeb(query: String): CommandResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.google.com/search?q=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult(true, "Searching Google for $query")
        } catch (e: ActivityNotFoundException) {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun dialNumber(command: String): CommandResult {
        val number = command.filter { it.isDigit() || it == '+' }
        if (number.isNotBlank()) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(callIntent)
                CommandResult(true, "Calling $number")
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                CommandResult(true, "Opening dialer for $number")
            }
        }
        return CommandResult(false, "Please say the phone number you want to call.")
    }

    private fun sendSms(command: String): CommandResult {
        val parts = command.split("to ")
        if (parts.size < 2) {
            return CommandResult(false, "Please specify who to send the message to.")
        }
        val targetPart = parts[1]
        val messageSplit = targetPart.split(" saying ", " message ", " that ")
        if (messageSplit.size < 2) {
            return CommandResult(false, "Please include the message content.")
        }
        val recipient = messageSplit[0].trim()
        val message = messageSplit[1].trim()

        val smsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:")
            putExtra("address", recipient)
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(smsIntent)
            CommandResult(true, "Drafting SMS to $recipient.")
        } catch (e: ActivityNotFoundException) {
            // Fallback to SmsManager if permission granted
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager.getDefault().sendTextMessage(recipient, null, message, null, null)
                CommandResult(true, "SMS sent to $recipient.")
            } else {
                CommandResult(false, context.getString(R.string.permission_sms_rationale))
            }
        }
    }

    private fun addCalendarEvent(raw: String): CommandResult {
        val now = Calendar.getInstance()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, raw)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now.timeInMillis + 60 * 60 * 1000)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, now.timeInMillis + 2 * 60 * 60 * 1000)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, "Opening calendar to add your event.")
        } catch (e: ActivityNotFoundException) {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun openCamera(): CommandResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult(true, "Opening camera.")
        } catch (e: ActivityNotFoundException) {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun playMusic(): CommandResult {
        val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult(true, "Starting your default music app.")
        } catch (e: ActivityNotFoundException) {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun setAlarm(raw: String): CommandResult {
        val numbers = raw.filter { it.isDigit() || it == ':' }
        val parts = numbers.split(":")
        val hour: Int
        val minutes: Int
        if (parts.size >= 2) {
            hour = parts[0].toIntOrNull() ?: 7
            minutes = parts[1].toIntOrNull() ?: 0
        } else {
            hour = 7
            minutes = 0
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Panda AI Alarm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CommandResult(true, "Setting alarm for $hour:${minutes.toString().padStart(2, '0')}")
        } catch (e: ActivityNotFoundException) {
            CommandResult(false, context.getString(R.string.app_not_found))
        }
    }

    private fun tellTime(): CommandResult {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return CommandResult(true, "It is ${formatter.format(Date())}")
    }

    private fun tellDate(): CommandResult {
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return CommandResult(true, "Today is ${formatter.format(Date())}")
    }
}
