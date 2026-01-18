package win.qiangge.comfydroid.model

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generation_results ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GenerationResult>>

    @Query("SELECT * FROM generation_results WHERE status = 'PENDING'")
    suspend fun getPendingResults(): List<GenerationResult>

    @Insert
    suspend fun insert(result: GenerationResult)


    @Update
    suspend fun update(result: GenerationResult)
    
    @Query("DELETE FROM generation_results WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Database(entities = [GenerationResult::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun generationDao(): GenerationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comfydroid_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
