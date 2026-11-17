package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class BackgroundFileProvider: FileProvider()

/** Private, downsampled background copies survive gallery permission changes. */
class BackgroundPhotos(private val context: Context) {
    private val directory=File(context.filesDir,"backgrounds").apply {mkdirs()}
    private fun file(name: String): File {
        require(name.matches(Regex("(?:bg|capture)_[a-f0-9-]+\\.jpg"))) {"Invalid background filename"}
        return File(directory,name)
    }
    fun newCapture(): Pair<String,Uri> {
        val name="capture_${UUID.randomUUID()}.jpg"
        val output=file(name);output.createNewFile()
        return name to FileProvider.getUriForFile(context,"${context.packageName}.backgroundfiles",output)
    }
    fun discardCapture(name: String?) {
        if(name!=null && name.startsWith("capture_")) runCatching {file(name).delete()}
    }
    private fun decode(source: File,maxEdge: Int=1600): Bitmap {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeFile(source.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) {"This file is not a supported image"}
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge) sample*=2
        val decoded=BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply {inSampleSize=sample})
            ?: error("Could not decode image")
        val orientation=runCatching {ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix()
        when(orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {matrix.setRotate(90f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {matrix.setRotate(-90f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val oriented=if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if(oriented!==decoded) decoded.recycle()
        val ratio=minOf(1.0,maxEdge.toDouble()/maxOf(oriented.width,oriented.height))
        val result=if(ratio<1) Bitmap.createScaledBitmap(oriented,maxOf(1,(oriented.width*ratio).toInt()),maxOf(1,(oriented.height*ratio).toInt()),true) else oriented
        if(result!==oriented) oriented.recycle()
        return result
    }
    private fun normalize(source: File): String {
        val bitmap=decode(source)
        val name="bg_${UUID.randomUUID()}.jpg";val target=file(name)
        try {
            target.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) {"Could not save background"}}
        } catch(e: Exception) {target.delete();throw e}
        finally {bitmap.recycle()}
        return name
    }
    fun importImage(uri: Uri): String {
        val temp=File.createTempFile("import_",".image",context.cacheDir)
        try {
            val input=context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
            input.use {source -> temp.outputStream().use {output ->
                val buffer=ByteArray(8192);var total=0L
                while(true) {
                    val count=source.read(buffer);if(count<0) break
                    total+=count;require(total<=40L*1024*1024) {"Choose an image smaller than 40 MB"}
                    output.write(buffer,0,count)
                }
            }}
            return normalize(temp)
        } finally {temp.delete()}
    }
    fun finishCapture(name: String): String = try {normalize(file(name))} finally {discardCapture(name)}
    fun load(name: String): Bitmap = decode(file(name))
}
