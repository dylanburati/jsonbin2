package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.support.postgresql.PostgreSqlDialect
import org.flywaydb.core.Flyway
import java.util.*

class ServiceBuilder : AutoCloseable {
  companion object {
    private val defaultHikariConfig: HikariConfig = HikariConfig().apply {
      jdbcUrl = Config.Database.url
      username = Config.Database.user
      password = Config.Database.password
      dataSourceProperties = Properties().apply {
        setProperty("stringtype", "unspecified")
      }
    }

    fun runMigrations() {
      Config.Database.run {
        Flyway.configure().dataSource(url, user, password).load().migrate()
      }
    }
  }

  private val dataSource = HikariDataSource(defaultHikariConfig)

  fun getServices() = ServiceContainer(this, Database.connect(dataSource, PostgreSqlDialect()))

  override fun close() {
    this.dataSource.close()
  }
}
