package com.wein.fasttrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, TagEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the tags table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isPreset` INTEGER NOT NULL)"
                )
                // Add unique index on name
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
                )
                // Insert preset tags
                db.execSQL("INSERT OR IGNORE INTO `tags` (`name`, `isPreset`) VALUES ('Umum', 1), ('Makan', 1), ('Transport', 1), ('Belanja', 1), ('Tagihan', 1), ('Jajan', 1)")
                
                // Normalize legacy tags
                db.execSQL("UPDATE `expenses` SET `tag` = 'Umum' WHERE `tag` = 'General' OR `tag` IS NULL OR `tag` = ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fast_track_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
