package com.example.adi.hotspotbucket.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "placesTable")
data class PlaceEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val image: String,
    val description: String,
    val date: String,
    val location: String,
    val latitude: Double,
    val longitude: Double
)