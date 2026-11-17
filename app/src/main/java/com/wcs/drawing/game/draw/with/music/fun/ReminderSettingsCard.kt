package com.wcs.drawing.game.draw.with.music.`fun`

import android.Manifest
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun ReminderSettingsCard() {
    val context=LocalContext.current
    val forest=Color(0xFF263F37)
    val mint=Color(0xFF08BFB3)
    var optedIn by remember {mutableStateOf(DrawingReminders.enabled(context))}
    val owner=remember(context) {
        generateSequence(context) {(it as? ContextWrapper)?.baseContext}.filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_RESUME) {
                optedIn=DrawingReminders.enabled(context)
                DrawingReminders.scheduleNext(context)
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {owner?.lifecycle?.removeObserver(observer)}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        optedIn=granted && DrawingReminders.canNotify(context)
        DrawingReminders.setEnabled(context,optedIn)
    }
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=Color(0xFFF0F4EE)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Creative reminder",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,color=forest)
            Switch(checked=optedIn,onCheckedChange={enabled ->
                DrawingReminders.createChannel(context)
                if(!enabled) {DrawingReminders.setEnabled(context,false);optedIn=false}
                else if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if(DrawingReminders.canNotify(context)) {
                    DrawingReminders.setEnabled(context,true);optedIn=true
                } else {
                    runCatching {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
                }
            },colors=SwitchDefaults.colors(checkedTrackColor=mint))
        }
    }
}
