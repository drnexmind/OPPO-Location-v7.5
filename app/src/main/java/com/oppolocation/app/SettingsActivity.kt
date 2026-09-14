package com.oppolocation.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oppolocation.app.databinding.ActivitySettingsBinding

class SettingsActivity:AppCompatActivity(){
    private lateinit var b:ActivitySettingsBinding
    private lateinit var p:AppPrefs
    override fun onCreate(s:Bundle?){ super.onCreate(s); b=ActivitySettingsBinding.inflate(layoutInflater); setContentView(b.root); p=AppPrefs(this)
        b.etInterval.setText(p.updateIntervalMs.toString()); b.etAccuracy.setText(p.accuracy.toString()); b.etAltitude.setText(p.altitude.toString()); b.etSpeed.setText(p.speedMps.toString()); b.etBearing.setText(p.bearing.toString())
        b.btnSave.setOnClickListener{
            p.updateIntervalMs=b.etInterval.text.toString().toLongOrNull()?:1000
            p.accuracy=b.etAccuracy.text.toString().toFloatOrNull()?:3f
            p.altitude=b.etAltitude.text.toString().toDoubleOrNull()?:0.0
            p.speedMps=b.etSpeed.text.toString().toFloatOrNull()?:0f
            p.bearing=b.etBearing.text.toString().toFloatOrNull()?:0f
            Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show(); finish()
        }
    }
}
