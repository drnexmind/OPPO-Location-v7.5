package com.oppolocation.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class LocationWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context, manager:AppWidgetManager, ids:IntArray){ ids.forEach{ update(context,manager,it) } }
    companion object {
        fun refreshAll(context:Context){ val m=AppWidgetManager.getInstance(context); val c=ComponentName(context,LocationWidgetProvider::class.java); m.getAppWidgetIds(c).forEach{update(context,m,it)} }
        private fun update(context:Context,m:AppWidgetManager,id:Int){
            val p=AppPrefs(context); val rv=RemoteViews(context.packageName,R.layout.widget_location)
            rv.setTextViewText(R.id.widgetTitle, if(p.running) "OPPO Location • ACTIVE" else "OPPO Location")
            rv.setTextViewText(R.id.widgetPlace,"${p.lastName}\n${"%.5f".format(p.lastLat)}, ${"%.5f".format(p.lastLon)}")
            fun pi(action:String,code:Int):PendingIntent { val i=Intent(context,MockLocationService::class.java).setAction(action).putExtra(MockLocationService.EXTRA_LAT,p.lastLat).putExtra(MockLocationService.EXTRA_LON,p.lastLon).putExtra(MockLocationService.EXTRA_NAME,p.lastName); return PendingIntent.getService(context,code,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
            rv.setOnClickPendingIntent(R.id.widgetStart,pi(MockLocationService.ACTION_START,1001)); rv.setOnClickPendingIntent(R.id.widgetStop,pi(MockLocationService.ACTION_STOP,1002))
            val open=PendingIntent.getActivity(context,1003,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); rv.setOnClickPendingIntent(R.id.widgetPlace,open)
            m.updateAppWidget(id,rv)
        }
    }
}
