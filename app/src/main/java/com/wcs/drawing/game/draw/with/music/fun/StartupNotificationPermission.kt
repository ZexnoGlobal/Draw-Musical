package com.wcs.drawing.game.draw.with.music.`fun`

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// No Activity reference. Survives rotation while Android owns the permission dialog.
private object StartupReminderPrompt {
    var pending by mutableStateOf(false)
}

/** Called only after language/onboarding, before the App Open gate is entered. */
@Composable
fun StartupNotificationPermissionGate():Boolean {
    val context=LocalContext.current
    val prefs=remember(context) {context.applicationContext.getSharedPreferences("drawing_reminders_v1",Context.MODE_PRIVATE)}
    var finished by remember {mutableStateOf(
        !StartupReminderPrompt.pending && (prefs.getBoolean("startup_prompt_seen",false) || prefs.contains("enabled")))}
    if(finished) return true

    fun complete(allow:Boolean) {
        prefs.edit().putBoolean("startup_prompt_seen",true).apply()
        DrawingReminders.setEnabled(context,allow && DrawingReminders.canNotify(context))
        StartupReminderPrompt.pending=false
        finished=true
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        complete(granted)
    }
    if(Build.VERSION.SDK_INT>=33) {
        LaunchedEffect(Unit) {
            if(!StartupReminderPrompt.pending) {
                DrawingReminders.createChannel(context)
                if(DrawingReminders.canNotify(context)) complete(true)
                else {
                    // Mark before launching so denial/process death cannot cause repeat startup prompts.
                    prefs.edit().putBoolean("startup_prompt_seen",true).apply()
                    StartupReminderPrompt.pending=true
                    try {permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
                    catch(e:Exception) {complete(false)}
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize(),color=Color(0xFFFFFEF8)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            DrawMusicalBrandIcon(Modifier.size(150.dp))
            Spacer(Modifier.height(24.dp))
            Text("Draw Musical",style=MaterialTheme.typography.headlineLarge,color=Color(0xFF263F37))
            Spacer(Modifier.height(20.dp))
            Text("Allow notifications for gentle drawing reminders.",color=Color(0xFF263F37))
        }
    }
    if(Build.VERSION.SDK_INT<33) AlertDialog(
        onDismissRequest={complete(false)},containerColor=Color(0xFFFFFEF8),
        title={Text("Allow creative reminders?")},
        text={Text("Get one illustrated drawing reminder around 7 PM. You can turn it off in Settings anytime.")},
        confirmButton={Button(onClick={DrawingReminders.createChannel(context);complete(true)},
            colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF08BFB3))) {Text("Allow")}},
        dismissButton={TextButton(onClick={complete(false)}) {Text("Not now")}})
    return finished
}
