package com.iticbcn.kronos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iticbcn.kronos.data.local.db.converters.Converters
import com.iticbcn.kronos.data.local.db.dao.ObjecteUEDao
import com.iticbcn.kronos.data.local.db.entities.ObjecteUE

@Database(
    entities = [ObjecteUE::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ueDao(): ObjecteUEDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kronos_db"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}