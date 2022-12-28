package com.example.adi.hotspotbucket.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlacesDao {

    @Query("select * from `placesTable`")
    fun fetchAllPlaces(): Flow<List<PlaceEntity>>

    @Insert
    suspend fun insert(placeEntity: PlaceEntity)

    @Update
    suspend fun update(placeEntity: PlaceEntity)

    @Delete
    suspend fun delete(placeEntity: PlaceEntity)
}