package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import org.json.JSONObject

class ExportFileProvider:FileProvider()
enum class ExportKind(val extension:String,val mime:String,val title:String,val subtitle:String) {
    MIDI("mid","audio/midi","♪ Music notes","MIDI · Notes, not the exact sound"),
    WAV("wav","audio/wav","◖ Audio recording","WAV · One loop + release tail"),
    MP4("mp4","video/mp4","▶ Video recording","MP4 · Drawing replay with sound")
}
data class ExportedFile(val id:String,val name:String,val kind:ExportKind,val created:Long,
    val publicUri:String?,val projectKey:String)

class ExportLibrary(private val context:Context) {
    private val prefs=context.getSharedPreferences("draw_music_exports_v1",Context.MODE_PRIVATE)
    private val folder=File(context.filesDir,"exports").apply {mkdirs()}
    fun file(entry:ExportedFile)=File(folder,entry.name)
    fun createFile(kind:ExportKind):File=File(folder,"DrawMusic_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.${kind.extension}")
    fun list():List<ExportedFile> = prefs.all.keys.mapNotNull {key ->
        runCatching {
            val j=JSONObject(prefs.getString(key,null) ?: return@runCatching null)
            ExportedFile(key,j.getString("name"),ExportKind.valueOf(j.getString("kind")),j.getLong("created"),
                j.optString("uri").takeIf {it.isNotBlank()},j.getString("project"))
        }.getOrNull()
    }.sortedByDescending {it.created}
    fun add(file:File,kind:ExportKind,project:MelodyProject):ExportedFile {
        require(file.isFile && file.length()>0)
        val id=java.util.UUID.randomUUID().toString()
        val projectKey="export_snapshot_$id"
        MelodyStore(context).save(projectKey,project)
        val uri=publish(file,kind)
        val entry=ExportedFile(id,file.name,kind,System.currentTimeMillis(),uri?.toString(),projectKey)
        val j=JSONObject().put("name",entry.name).put("kind",kind.name).put("created",entry.created)
            .put("uri",entry.publicUri ?: "").put("project",projectKey)
        if(!prefs.edit().putString(id,j.toString()).commit()) {
            uri?.let {runCatching {context.contentResolver.delete(it,null,null)}}
            error("Could not index exported file")
        }
        return entry
    }
    /** Delete only this indexed export and its app-created public/private copies. */
    fun delete(entry:ExportedFile) {
        val stored=list().firstOrNull {it.id==entry.id} ?: return
        val privateFile=file(stored)
        require(privateFile.canonicalFile.parentFile==folder.canonicalFile) {"Invalid export path"}
        stored.publicUri?.let {raw ->
            val publicFile=Uri.parse(raw)
            require(publicFile.scheme=="content" && publicFile.authority==MediaStore.AUTHORITY) {"Invalid export URI"}
            // App-created MediaStore rows normally need no extra permission. A permission
            // failure is reported to the user, retaining the card so they can retry.
            context.contentResolver.delete(publicFile,null,null)
        }
        check(!privateFile.exists() || privateFile.delete()) {"Could not delete the exported file"}
        MelodyStore(context).deleteExportSnapshot(stored.projectKey)
        check(prefs.edit().remove(stored.id).commit()) {"Could not remove this Library entry"}
    }
    private fun publish(file:File,kind:ExportKind):Uri? {
        if(Build.VERSION.SDK_INT<29) return null
        val resolver=context.contentResolver
        val collection=when(kind) {
            ExportKind.WAV ->MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ExportKind.MP4 ->MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ExportKind.MIDI ->MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val path=when(kind) {ExportKind.WAV->"Music/DrawMusic";ExportKind.MP4->"Movies/DrawMusic";else->"Download/DrawMusic"}
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,file.name);put(MediaStore.MediaColumns.MIME_TYPE,kind.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,path);put(MediaStore.MediaColumns.IS_PENDING,1)
            if(kind==ExportKind.WAV) {put(MediaStore.Audio.Media.IS_MUSIC,1);put(MediaStore.Audio.Media.TITLE,file.name.removeSuffix(".wav"));put(MediaStore.Audio.Media.ARTIST,"Draw Musical")}
        }
        var uri:Uri?=null
        return try {
            uri=resolver.insert(collection,values) ?: error("Cannot create public file")
            requireNotNull(resolver.openOutputStream(uri)).use {out ->file.inputStream().use {it.copyTo(out)}}
            check(resolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)>0)
            uri
        } catch(e:Exception) {uri?.let {runCatching {resolver.delete(it,null,null)}};null}
    }
    fun uri(entry:ExportedFile):Uri {
        // Prefer the permanent public copy; keep an app-owned copy for fallback and Save a copy.
        entry.publicUri?.let {raw ->
            val uri=Uri.parse(raw)
            if(runCatching {context.contentResolver.openFileDescriptor(uri,"r")?.use {true} ?: false}.getOrDefault(false)) return uri
        }
        check(file(entry).isFile) {"This exported file is no longer available"}
        return FileProvider.getUriForFile(context,"${context.packageName}.exports",file(entry))
    }
    fun open(entry:ExportedFile) {
        val uri=uri(entry)
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,entry.kind.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=ClipData.newRawUri(entry.name,uri)}
        context.startActivity(Intent.createChooser(intent,"Open ${entry.kind.extension.uppercase()} with")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    fun share(entry:ExportedFile) {
        val uri=uri(entry)
        val intent=Intent(Intent.ACTION_SEND).setType(entry.kind.mime).putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=ClipData.newRawUri(entry.name,uri)}
        context.startActivity(Intent.createChooser(intent,"Share ${entry.name}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

