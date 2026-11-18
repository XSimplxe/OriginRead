package com.yuanyue.reader.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuanyue.reader.model.BookEntity
import com.yuanyue.reader.model.BookFormat
import com.yuanyue.reader.model.BookSourceEntity
import com.yuanyue.reader.model.CachedChapterContentEntity
import com.yuanyue.reader.model.ComicChapterEntity
import com.yuanyue.reader.model.ComicSourceEntity
import com.yuanyue.reader.model.OnlineBookEntity
import com.yuanyue.reader.model.OnlineChapterEntity
import com.yuanyue.reader.model.OnlineComicEntity
import com.yuanyue.reader.model.ReaderAnnotationEntity
import com.yuanyue.reader.model.RssArticleEntity
import com.yuanyue.reader.model.RssFeedEntity

@Database(
    entities = [
        RssFeedEntity::class,
        RssArticleEntity::class,
        BookEntity::class,
        BookSourceEntity::class,
        OnlineBookEntity::class,
        OnlineChapterEntity::class,
        CachedChapterContentEntity::class,
        ComicSourceEntity::class,
        OnlineComicEntity::class,
        ComicChapterEntity::class,
        ReaderAnnotationEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rssFeedDao(): RssFeedDao
    abstract fun rssArticleDao(): RssArticleDao
    abstract fun bookDao(): BookDao
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun onlineBookDao(): OnlineBookDao
    abstract fun onlineChapterDao(): OnlineChapterDao
    abstract fun cachedContentDao(): CachedContentDao
    abstract fun comicSourceDao(): ComicSourceDao
    abstract fun onlineComicDao(): OnlineComicDao
    abstract fun comicChapterDao(): ComicChapterDao
    abstract fun readerAnnotationDao(): ReaderAnnotationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN readingSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reader_annotations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookKey TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        paragraphIndex INTEGER NOT NULL DEFAULT -1,
                        text TEXT NOT NULL,
                        note TEXT,
                        type TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reader_annotations_bookKey_chapterIndex_pageIndex " +
                        "ON reader_annotations(bookKey, chapterIndex, pageIndex)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE online_books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yuanyue_reader.db",
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
