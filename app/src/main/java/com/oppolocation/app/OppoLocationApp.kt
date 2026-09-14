package com.oppolocation.app

import android.app.Application
import io.objectbox.BoxStore

class OppoLocationApp : Application() {

    lateinit var boxStore: BoxStore
        private set

    override fun onCreate() {
        super.onCreate()

        boxStore = MyObjectBox.builder()
            .androidContext(this)
            .build()
    }
}
