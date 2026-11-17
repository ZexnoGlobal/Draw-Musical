package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

private val StartupCream=Color(0xFFFFFEF8)
private val StartupForest=Color(0xFF263F37)
private val StartupMint=Color(0xFF08BFB3)

@Composable
fun StartupFlow(audio:MelodyAudio) {
    val context=LocalContext.current
    val preferences=remember {context.getSharedPreferences("draw_music_settings",0)}
    var language by remember {mutableStateOf(AppLanguage.values().firstOrNull {
        it.code==preferences.getString("language",null)
    } ?: AppLanguage.ENGLISH)}
    var chosen by remember {mutableStateOf(preferences.contains("language"))}
    var onboarded by remember {mutableStateOf(preferences.getBoolean("onboarding_complete",false))}
    var ready by rememberSaveable {mutableStateOf(false)}
    var chooseLanguage by rememberSaveable {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var attempt by remember {mutableIntStateOf(0)}
    LaunchedEffect(attempt) {
        if(!ready) {
            error=null
            try {
                val start=android.os.SystemClock.elapsedRealtime()
                withContext(Dispatchers.IO) {
                    MelodyStore(context.applicationContext).migrateWork()
                    TemplateCatalog.load(context.applicationContext)
                }
                // A short minimum makes the loading page visible without a long artificial delay.
                delay((700-(android.os.SystemClock.elapsedRealtime()-start)).coerceAtLeast(0L))
                ready=true
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error=e.message ?: "Loading failed"}
        }
    }
    CompositionLocalProvider(LocalAppLanguage provides language,
        LocalRequestLanguage provides {chooseLanguage=true},
        LocalLayoutDirection provides if(language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        when {
            !ready ->LoadingPage(error,onRetry={attempt++})
            else ->Box(Modifier.fillMaxSize()) {
                if(chosen && onboarded) MusicAppRoot(audio)
                if(chosen && !onboarded && !chooseLanguage) OnboardingFlow(onFinish={
                    preferences.edit().putBoolean("onboarding_complete",true).apply()
                    onboarded=true
                })
                if(!chosen || chooseLanguage) {
                    BackHandler(enabled=chooseLanguage && chosen) {chooseLanguage=false}
                    LanguageSelectionPage(language,onSelect={selected ->
                        preferences.edit().putString("language",selected.code).apply()
                        language=selected;chosen=true;chooseLanguage=false
                    })
                }
            }
        }
    }
}

@Composable
private fun BrandName() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Text("Draw ",fontSize=42.sp,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=StartupForest)
            Text("Musical",fontSize=42.sp,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=StartupMint)
        }
    }
}

@Composable
private fun LoadingPage(error:String?,onRetry:()->Unit) {
    Surface(modifier=Modifier.fillMaxSize(),color=StartupCream) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            DrawMusicalBrandIcon(Modifier.size(150.dp))
            Spacer(Modifier.height(24.dp));BrandName()
            if(error==null) {
                CircularProgressIndicator(Modifier.size(32.dp),color=StartupMint,strokeWidth=3.dp)
                Text(uiText("Loading…"),color=StartupForest,modifier=Modifier.padding(top=18.dp),textAlign=TextAlign.Center)
            } else {
                Text(uiText("Could not prepare the app"),color=StartupForest,fontWeight=FontWeight.Bold)
                Text("Please try again.",color=StartupForest.copy(alpha=.7f),textAlign=TextAlign.Center,modifier=Modifier.padding(12.dp))
                Button(onClick=onRetry,colors=ButtonDefaults.buttonColors(containerColor=StartupMint)) {Text(uiText("Retry"))}
            }
        }
    }
}

@Composable
private fun LanguageSelectionPage(current:AppLanguage,onSelect:(AppLanguage)->Unit) {
    var selection by remember(current) {mutableStateOf(current)}
    // Preview translated chooser text and direction before committing the selection.
    CompositionLocalProvider(LocalAppLanguage provides selection,
        LocalLayoutDirection provides if(selection.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Surface(modifier=Modifier.fillMaxSize(),color=StartupCream) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp,vertical=20.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                BrandName();Spacer(Modifier.height(28.dp))
                Text(uiText("Choose your language"),fontSize=27.sp,lineHeight=34.sp,
                    fontWeight=FontWeight.Bold,color=StartupForest,textAlign=TextAlign.Center)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),
                    contentPadding=PaddingValues(vertical=12.dp)) {
                    items(AppLanguage.values().toList(),key={it.code}) {option ->
                        val checked=selection==option
                        Surface(modifier=Modifier.fillMaxWidth(),onClick={selection=option},
                            shape=RoundedCornerShape(20.dp),color=if(checked) Color(0xFFE5F7F2) else Color.White,
                            border=BorderStroke(if(checked) 2.dp else 1.dp,if(checked) StartupMint else Color(0xFFE5E7E0))) {
                            Row(Modifier.padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.nativeName,fontSize=22.sp,color=StartupForest)
                                    Text(option.code.uppercase(),fontSize=12.sp,color=StartupForest.copy(alpha=.55f))
                                }
                                RadioButton(selected=checked,onClick={selection=option},
                                    colors=RadioButtonDefaults.colors(selectedColor=StartupMint))
                            }
                        }
                    }
                }
                Button(onClick={onSelect(selection)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),
                    shape=RoundedCornerShape(32.dp),colors=ButtonDefaults.buttonColors(containerColor=StartupMint)) {
                    Text(uiText("Continue"),fontSize=20.sp,color=Color.White)
                }
            }
        }
    }
}
