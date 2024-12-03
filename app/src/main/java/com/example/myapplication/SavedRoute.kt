package com.example.myapplication

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "saved_routes")
data class SavedRoute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeNum: String,
    val routeTitle: String,
    val coordinates: String // Store coordinates as a String for simplicity
)

@Dao
interface SavedRouteDao {
    @Insert
    fun insert(savedRoute: SavedRoute)

    @Query("SELECT * FROM saved_routes")
    fun getAllSavedRoutes(): List<SavedRoute>

    @Delete
    fun delete(savedRoute: SavedRoute)
}

@Database(entities = [SavedRoute::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedRouteDao(): SavedRouteDao
}