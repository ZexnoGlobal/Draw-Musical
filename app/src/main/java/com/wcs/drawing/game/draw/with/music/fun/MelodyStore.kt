package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedDrawing(val id:String,val title:String,val updated:Long,val project:MelodyProject,val exported:Boolean=false)

class MelodyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("melody_projects_v2", Context.MODE_PRIVATE)
    fun save(key: String, project: MelodyProject) {
        val strokes = JSONArray()
        project.strokes.forEach { stroke ->
            val cells = JSONArray()
            stroke.cells.forEach { cells.put(JSONArray().put(it.x).put(it.y).put(it.instrument)) }
            strokes.put(cells)
        }
        val visuals=JSONArray()
        project.visuals.forEach { stroke ->
            val points=JSONArray()
            stroke.points.forEach { points.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
            visuals.put(JSONObject().put("instrument",stroke.instrument).put("width",stroke.widthCells.toDouble()).put("points",points))
        }
        val json = JSONObject().put("version", 4).put("strokes", strokes)
            .put("visuals",visuals).put("smooth",project.smooth).put("background",project.backgroundIndex)
            .put("backgroundPhoto",project.backgroundPhoto ?: JSONObject.NULL)
            .put("backgroundOpacity",project.backgroundOpacity.toDouble())
            .put("tempo", project.tempo).put("root", project.root).put("scale", project.scale)
            .put("backingSounds",JSONArray(project.backingSounds.sorted()))
            .put("loop", project.loop).put("instrument", project.instrument)
        val edit=prefs.edit().putString(key,json.toString())
        if(key=="draft") {
            val id=prefs.getString("active_work",null)
            if(id!=null) {
                if(prefs.getString("work_$id",null)!=json.toString()) {
                    edit.putLong("updated_$id",System.currentTimeMillis()).putBoolean("exported_$id",false)
                }
                edit.putString("work_$id",json.toString())
            }
        }
        edit.apply()
    }
    fun beginWork(project:MelodyProject,title:String):String {
        val id=java.util.UUID.randomUUID().toString()
        prefs.edit().putString("active_work",id).putString("title_$id",title)
            .putLong("created_$id",System.currentTimeMillis()).putBoolean("exported_$id",false).apply()
        save("draft",project)
        return id
    }
    fun openWork(id:String):Boolean {
        val project=load("work_$id") ?: return false
        prefs.edit().putString("active_work",id).apply()
        save("draft",project)
        return true
    }
    fun deleteWork(id:String) {
        val edit=prefs.edit().remove("work_$id").remove("title_$id")
            .remove("created_$id").remove("updated_$id").remove("exported_$id")
        if(prefs.getString("active_work",null)==id) edit.remove("active_work").remove("draft")
        check(edit.commit()) {"Could not delete this draft"}
    }
    fun deleteExportSnapshot(key:String) {
        require(key.startsWith("export_snapshot_"))
        check(prefs.edit().remove(key).commit()) {"Could not remove export snapshot"}
    }
    fun markExported() {
        prefs.getString("active_work",null)?.let {prefs.edit().putBoolean("exported_$it",true).apply()}
    }
    fun workItems():List<SavedDrawing> = prefs.all.keys.filter {it.startsWith("work_")}.mapNotNull {key ->
        val id=key.removePrefix("work_")
        load(key)?.let {SavedDrawing(id,prefs.getString("title_$id","Draw") ?: "Draw",
            prefs.getLong("updated_$id",prefs.getLong("created_$id",0)),it,prefs.getBoolean("exported_$id",false))}
    }.sortedByDescending {it.updated}
    fun migrateWork() {
        if(prefs.getBoolean("library_migrated",false)) return
        // Retain every surviving old save. No empty draft is created just by visiting Home.
        val keys=listOf("previous_draft","slot1","slot2","slot3","draft")
        val oldDraft=load("draft")
        val seen=mutableSetOf<String>()
        val snapshots=keys.mapNotNull {key ->
            val raw=prefs.getString(key,null)
            val project=load(key)
            if(raw!=null && project!=null && seen.add(raw)) Triple(key,raw,project) else null
        }
        snapshots.forEach {(key,_,project) ->
            beginWork(project,if(key.startsWith("slot")) "Saved ${key.removePrefix("slot")}" else "Draw")
        }
        if(oldDraft!=null) {
            val match=workItems().firstOrNull {it.project==oldDraft}
            if(match!=null) openWork(match.id)
        } else prefs.edit().remove("active_work").remove("draft").apply()
        prefs.edit().putBoolean("library_migrated",true).apply()
    }
    fun load(key: String): MelodyProject? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val json=JSONObject(raw); val version=json.getInt("version")
            require(version in 1..4)
            val oldInstrumentMap=intArrayOf(0,1,3,2)
            fun instrument(value: Int): Int {
                require(value in 0..(if(version==1) 3 else instruments.lastIndex))
                return if(version==1) oldInstrumentMap[value] else value
            }
            val array=json.getJSONArray("strokes")
            val strokes=(0 until array.length()).map { i ->
                val cells=array.getJSONArray(i)
                Stroke((0 until cells.length()).map { j ->
                    val c=cells.getJSONArray(j); val x=c.getInt(0); val y=c.getInt(1)
                    require(x in 0 until (if(version==1) 32 else MUSIC_COLUMNS))
                    require(y in 0 until (if(version==1) 20 else MUSIC_ROWS))
                    Cell(if(version==1) x*MUSIC_COLUMNS/32 else x,
                        if(version==1) y*MUSIC_ROWS/20 else y, instrument(c.getInt(2)))
                })
            }
            val visuals=mutableListOf<VisualStroke>()
            json.optJSONArray("visuals")?.let { paths ->
                for(i in 0 until paths.length()) {
                    val path=paths.getJSONObject(i);val rawPoints=path.getJSONArray("points")
                    val points=(0 until rawPoints.length()).map { j ->
                        val point=rawPoints.getJSONArray(j)
                        val x=point.getDouble(0).toFloat();val y=point.getDouble(1).toFloat()
                        require(x.isFinite() && y.isFinite() && x in 0f..MUSIC_COLUMNS.toFloat() && y in 0f..MUSIC_ROWS.toFloat())
                        DrawPoint(x,y)
                    }
                    val width=path.optDouble("width",.14).toFloat()
                    require(width.isFinite())
                    visuals+=VisualStroke(instrument(path.getInt("instrument")),points,width.coerceIn(.05f,3f))
                }
            }
            MelodyProject(strokes, json.getInt("tempo").coerceIn(40,240),
                json.getInt("root").coerceIn(0,11), json.getInt("scale").coerceIn(0,2),
                json.getBoolean("loop"), instrument(json.optInt("instrument",0)),
                visuals=visuals,smooth=json.optBoolean("smooth",false),
                backgroundIndex=json.optInt("background",0).coerceIn(0,canvasBackgrounds.lastIndex),
                backgroundPhoto=if(json.isNull("backgroundPhoto")) null else json.optString("backgroundPhoto").takeIf {
                    builtInBackgrounds.any {preset -> preset.id==it}
                },
                backgroundOpacity=json.optDouble("backgroundOpacity",1.0).toFloat().let {
                    if(it.isFinite()) it.coerceIn(0f,1f) else 1f
                },backingSounds=json.optJSONArray("backingSounds")?.let {items ->
                    (0 until items.length()).map {items.optString(it)}
                        .filter {id -> backingOptions.any {it.id==id}}.toSet()
                } ?: emptySet())
        } catch(e: Exception) {
            android.util.Log.e("DrawMusicStore","Could not load project",e); null
        }
    }
}
