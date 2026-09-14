package com.oppolocation.app

import android.content.Context

class AppPrefs(context: Context) {
    private val p=context.getSharedPreferences("oppo_location_settings",Context.MODE_PRIVATE)
    var updateIntervalMs: Long
        get()=p.getLong("interval",1000L)
        set(v)=p.edit().putLong("interval",v.coerceIn(250L,10000L)).apply()
    var accuracy: Float
        get()=p.getFloat("accuracy",3f)
        set(v)=p.edit().putFloat("accuracy",v.coerceIn(1f,100f)).apply()
    var altitude: Double
        get()=java.lang.Double.longBitsToDouble(p.getLong("altitude",java.lang.Double.doubleToLongBits(0.0)))
        set(v)=p.edit().putLong("altitude",java.lang.Double.doubleToLongBits(v)).apply()
    var speedMps: Float
        get()=p.getFloat("speed",0f)
        set(v)=p.edit().putFloat("speed",v.coerceIn(0f,100f)).apply()
    var bearing: Float
        get()=p.getFloat("bearing",0f)
        set(v)=p.edit().putFloat("bearing",((v%360)+360)%360).apply()
    var lastLat: Double
        get()=java.lang.Double.longBitsToDouble(p.getLong("last_lat",java.lang.Double.doubleToLongBits(33.3683)))
        set(v)=p.edit().putLong("last_lat",java.lang.Double.doubleToLongBits(v)).apply()
    var lastLon: Double
        get()=java.lang.Double.longBitsToDouble(p.getLong("last_lon",java.lang.Double.doubleToLongBits(6.8674)))
        set(v)=p.edit().putLong("last_lon",java.lang.Double.doubleToLongBits(v)).apply()
    var lastName: String
        get()=p.getString("last_name","El Oued") ?: "El Oued"
        set(v)=p.edit().putString("last_name",v).apply()
    var running: Boolean
        get()=p.getBoolean("running",false)
        set(v)=p.edit().putBoolean("running",v).apply()
}
