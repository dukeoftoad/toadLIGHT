package com.toadlight.filter

import android.app.Application
import android.content.Context
import com.toadlight.filter.core.AppGraph

class ToadlightApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as ToadlightApp).graph
