package com.transnote.traffic.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地 Room 数据库（底层 SQLite，纯本地存储，无服务端）。
 * 单例模式，供仓库层调用。
 */
@Database(
    entities = [
        CityEntity::class,
        BusLineEntity::class,
        BusStationEntity::class,
        MetroLineEntity::class,
        MetroStationEntity::class,
        MetroEntranceEntity::class,
        BusStationEntranceCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun busLineDao(): BusLineDao
    abstract fun busStationDao(): BusStationDao
    abstract fun metroLineDao(): MetroLineDao
    abstract fun metroStationDao(): MetroStationDao
    abstract fun metroEntranceDao(): MetroEntranceDao

    companion object {
        const val DB_NAME = "transnote.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // 初始化默认本地城市
                        db.execSQL(
                            "INSERT INTO city (cityId, cityName) VALUES (1, '默认城市')"
                        )
                    }
                })
                .fallbackToDestructiveMigration()
                .build()

        /** 备份导入后需要重开数据库连接，关闭当前实例 */
        fun close() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
