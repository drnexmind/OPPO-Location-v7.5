package com.oppolocation.app

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class SavedPlace(
    @Id var id: Long = 0,
    var name: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var favorite: Boolean = false,
    var lastUsed: Long = System.currentTimeMillis()
)
