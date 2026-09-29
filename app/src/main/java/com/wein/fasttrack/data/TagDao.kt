package com.wein.fasttrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY id ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)
    
    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int
    
    @Query("DELETE FROM tags WHERE isPreset = 0")
    suspend fun deleteNonPresetTags()
    
    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSync(): List<TagEntity>
}
