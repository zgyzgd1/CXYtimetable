package com.example.timetable.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.TimetableGroup
import com.example.timetable.data.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_groups ORDER BY updatedAt DESC, createdAt DESC")
    fun getGroupsStream(): Flow<List<TimetableGroup>>

    @Query("SELECT * FROM timetable_groups ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getGroups(): List<TimetableGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: TimetableGroup)

    @Query("SELECT * FROM timetable_entries WHERE groupId = :groupId ORDER BY date ASC, startMinutes ASC")
    fun getEntriesStream(groupId: String): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries WHERE groupId = :groupId ORDER BY date ASC, startMinutes ASC")
    suspend fun getEntries(groupId: String): List<TimetableEntry>

    @Query("SELECT * FROM timetable_entries ORDER BY date ASC, startMinutes ASC")
    suspend fun getAllEntries(): List<TimetableEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: TimetableEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<TimetableEntry>)

    @Query("DELETE FROM timetable_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM timetable_entries WHERE groupId = :groupId")
    suspend fun deleteEntriesInGroup(groupId: String)

    @Query("DELETE FROM timetable_entries")
    suspend fun deleteAll()
}
