package org.commonvoice.saverio_lib.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.commonvoice.saverio_lib.db.daos.*
import org.commonvoice.saverio_lib.models.*

@Database(
    entities = [Clip::class, Recording::class, Sentence::class, Validation::class, Report::class],
    version = 2,
    exportSchema = false //eventually set "true"
)
@TypeConverters(DBTypeConverters::class)
abstract class AppDB : RoomDatabase() {

    fun clearTables(): Unit = this.clearAllTables()

    abstract fun clips(): ClipsDAO
    abstract fun recordings(): RecordingsDAO
    abstract fun sentences(): SentencesDAO
    abstract fun validations(): ValidationsDAO
    abstract fun reports(): ReportsDAO

    companion object {

        fun build(ctx: Context): AppDB {
            return Room.databaseBuilder(ctx, AppDB::class.java, "local.db")
                .fallbackToDestructiveMigration()
                .build()
        }

    }

}