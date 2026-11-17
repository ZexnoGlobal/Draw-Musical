package com.wcs.drawing.game.draw.with.music.`fun`

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

internal fun nextReminderTime(now:Long,zone:ZoneId=ZoneId.systemDefault()):Long {
    val local=Instant.ofEpochMilli(now).atZone(zone)
    var target=local.toLocalDate().atTime(19,0).atZone(zone)
    if(!target.isAfter(local)) target=local.toLocalDate().plusDays(1).atTime(19,0).atZone(zone)
    return target.toInstant().toEpochMilli()
}

object DrawingReminders {
    private const val CHANNEL="drawing_breaks_v1"
    private const val ACTION="com.wcs.drawing.game.draw.with.music.fun.DRAWING_BREAK"
    private const val REQUEST=4101
    @Volatile var appVisible=false
    private val lock=Any()
    private fun prefs(context:Context)=context.applicationContext.getSharedPreferences("drawing_reminders_v1",Context.MODE_PRIVATE)
    fun enabled(context:Context)=prefs(context).getBoolean("enabled",false)
    fun createChannel(context:Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Creative breaks",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="A gentle daily reminder to draw and make music"
                enableVibration(false)
            })
    }
    fun canNotify(context:Context):Boolean {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    fun setEnabled(context:Context,value:Boolean) {
        prefs(context).edit().putBoolean("enabled",value).apply()
        if(value) {createChannel(context);scheduleNext(context)}
        else {
            context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
            NotificationManagerCompat.from(context).cancel(REQUEST)
            NotificationManagerCompat.from(context).cancel(REQUEST+1)
        }
    }
    private fun alarmIntent(context:Context)=PendingIntent.getBroadcast(context,REQUEST,
        Intent(context,DrawingReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun scheduleNext(context:Context) {
        val manager=context.getSystemService(AlarmManager::class.java)
        if(!enabled(context) || !canNotify(context)) {manager.cancel(alarmIntent(context));return}
        // Inexact reminder: no exact-alarm permission, service or network required.
        runCatching {manager.setWindow(AlarmManager.RTC_WAKEUP,nextReminderTime(System.currentTimeMillis()),
            60*60*1_000L,alarmIntent(context))}
            .onFailure {Log.w("DrawingReminders","Could not schedule reminder",it)}
    }
    fun receive(context:Context,action:String?) {
        if(action!=ACTION) {scheduleNext(context);return}
        try {
            synchronized(lock) {
                val local=Instant.now().atZone(ZoneId.systemDefault())
                val day=local.toLocalDate().toString()
                val settings=context.getSharedPreferences("draw_music_settings",Context.MODE_PRIVATE)
                // Never catch up during the night, interrupt an active editor, or send twice a day.
                if(enabled(context) && canNotify(context) && !appVisible && local.hour in 19..21 &&
                    settings.getBoolean("onboarding_complete",false) && prefs(context).getString("last_day",null)!=day) {
                    if(show(context,false)) prefs(context).edit().putString("last_day",day)
                        .putInt("message_index",prefs(context).getInt("message_index",0)+1).apply()
                }
            }
        } finally {scheduleNext(context)}
    }
    fun show(context:Context,preview:Boolean=true):Boolean {
        createChannel(context)
        if(!canNotify(context)) return false
        val messages=listOf(
            "🌿 Need a relaxation break?" to "Draw a little flower or bird and turn your sketch into a soothing melody. 🌸✨",
            "🎨 A little creativity awaits" to "Take a moment for yourself. A few playful lines can become your next melody.",
            "🎵 What will you draw today?" to "Make a tiny doodle, choose your favorite sound, and hear your imagination.",
            "✨ Your next melody starts with a line" to "Open Draw Musical for a gentle creative break. Your drawings are waiting for you.")
        val index=Math.floorMod(prefs(context).getInt("message_index",0),messages.size)
        val (title,message)=messages[index]
        val launch=PendingIntent.getActivity(context,REQUEST,
            Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val picture=Bitmap.createBitmap(800,420,Bitmap.Config.ARGB_8888)
        return try {
            val drawable=requireNotNull(ContextCompat.getDrawable(context,R.drawable.notification_break_art))
            drawable.setBounds(0,0,picture.width,picture.height);drawable.draw(Canvas(picture))
            val notification=NotificationCompat.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_music)
                .setContentTitle(title).setContentText(message)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).setSummaryText(message))
                .setColor(0xFF08BFB3.toInt()).setContentIntent(launch).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true).setTimeoutAfter(12*60*60*1_000L)
                .addAction(0,"Start drawing",launch).build()
            NotificationManagerCompat.from(context).notify(if(preview) REQUEST+1 else REQUEST,notification)
            true
        } catch(e:SecurityException) {false}
        catch(e:Exception) {Log.w("DrawingReminders","Could not show reminder",e);false}
        finally {picture.recycle()}
    }
}

class DrawingReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        runCatching {DrawingReminders.receive(context.applicationContext,intent.action)}
            .onFailure {Log.w("DrawingReminders","Reminder skipped",it)}
    }
}
