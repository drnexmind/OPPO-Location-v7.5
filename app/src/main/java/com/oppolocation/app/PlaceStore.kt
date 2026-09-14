package com.oppolocation.app

import android.content.Context
import io.objectbox.Box
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class PlaceStore(context: Context) {

    private val box: Box<SavedPlace> =
        (context.applicationContext as OppoLocationApp)
            .boxStore
            .boxFor(SavedPlace::class.java)

    fun addHistory(place: SavedPlace) = upsert(place, place.favorite)

    fun addFavorite(place: SavedPlace) = upsert(place, true)

    private fun upsert(place: SavedPlace, favorite: Boolean) {
        val existing = box.all.firstOrNull {
            abs(it.latitude - place.latitude) < 0.0000001 &&
            abs(it.longitude - place.longitude) < 0.0000001
        }

        val item = SavedPlace(
            id = existing?.id ?: 0,
            name = place.name,
            latitude = place.latitude,
            longitude = place.longitude,
            favorite = favorite || existing?.favorite == true,
            lastUsed = System.currentTimeMillis()
        )

        box.put(item)
        trimHistory()
    }

    private fun trimHistory() {
        val removable = box.all
            .filter { !it.favorite }
            .sortedByDescending { it.lastUsed }
            .drop(100)

        if (removable.isNotEmpty()) {
            box.remove(removable.toMutableList())
        }
    }

    fun favorites(): MutableList<SavedPlace> =
        box.all
            .filter { it.favorite }
            .sortedByDescending { it.lastUsed }
            .take(100)
            .toMutableList()

    fun history(): MutableList<SavedPlace> =
        box.all
            .sortedByDescending { it.lastUsed }
            .take(100)
            .toMutableList()

    fun exportJson(): String {
        val arr = JSONArray()
        history().forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("lat", p.latitude)
                    .put("lon", p.longitude)
                    .put("favorite", p.favorite)
                    .put("lastUsed", p.lastUsed)
            )
        }

        return JSONObject()
            .put("format", "oppo-location-places-v2-objectbox")
            .put("places", arr)
            .toString(2)
    }

    fun importJson(raw: String): Int {
        val root = JSONObject(raw)
        val arr = root.getJSONArray("places")
        var count = 0

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val p = SavedPlace(
                name = o.optString("name", "Imported place"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                favorite = o.optBoolean("favorite", false),
                lastUsed = o.optLong("lastUsed", System.currentTimeMillis())
            )

            upsert(p, p.favorite)
            count++
        }

        return count
    }
}
