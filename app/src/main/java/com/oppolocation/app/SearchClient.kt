package com.oppolocation.app

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder

class SearchClient {

    private val client = OkHttpClient()

    fun search(
        query: String,
        callback: (Result<SavedPlace>) -> Unit
    ) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url =
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OPPO-Location/2.0 Android")
            .header("Accept-Language", "en")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        val arr = JSONArray(body)
                        if (arr.length() == 0) {
                            Handler(Looper.getMainLooper()).post {
                                callback(Result.failure(IllegalStateException("No results")))
                            }
                            return
                        }

                        val first = arr.getJSONObject(0)
                        val place = SavedPlace(
                            name = first.optString("display_name", query),
                            latitude = first.getString("lat").toDouble(),
                            longitude = first.getString("lon").toDouble()
                        )

                        Handler(Looper.getMainLooper()).post {
                            callback(Result.success(place))
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            callback(Result.failure(e))
                        }
                    }
                }
            }
        })
    }
}
