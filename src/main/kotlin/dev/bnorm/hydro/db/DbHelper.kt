package dev.bnorm.hydro.db

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager

private val log = LogManager.getLogger(Database::class.java)

fun createDatabase(filePath: String = ""): Database {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY + filePath)
    val sqlCursor = driver.executeQuery(null, "PRAGMA user_version", 0, null)
    val currentVer = sqlCursor.use { sqlCursor.getLong(0)!!.toInt() }
    if (currentVer == 0) {
        Database.Schema.create(driver)
        val schemaVer = Database.Schema.version
        driver.execute(null, "PRAGMA user_version = $schemaVer", 0)
        log.debug("createDatabase: created tables, setVersion to {}", schemaVer)
    } else {
        val schemaVer = Database.Schema.version
        log.debug("createDatabase: current={} schema={}", currentVer, schemaVer)
        if (schemaVer > currentVer) {
            Database.Schema.migrate(driver, currentVer, schemaVer)
            driver.execute(null, "PRAGMA user_version = $schemaVer", 0)
            log.debug("createDatabase: migrated from {} to {}", currentVer, schemaVer)
        } else {
            log.debug("createDatabase: up-to-date at version {}", currentVer)
        }
    }
    return Database(driver, SensorReading.Adapter(object : ColumnAdapter<Instant, Long> {
        override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
        override fun encode(value: Instant): Long = value.toEpochMilliseconds()
    }))
}

