package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.support.postgresql.PostgreSqlDialect
import org.eclipse.jetty.util.log.Log
import org.flywaydb.core.Flyway

class ServiceBuilder : AutoCloseable {
  companion object {
    private val defaultHikariConfig: HikariConfig = HikariConfig().apply {
      jdbcUrl = Config.Database.url
      username = Config.Database.user
      password = Config.Database.password
    }

    fun runMigrations() {
      Config.Database.run {
        Flyway.configure().dataSource(url, user, password).load().migrate()
      }
    }
  }

  private val logger = Log.getLogger(ServiceBuilder::class.java)
  private val dataSource = HikariDataSource(defaultHikariConfig)

  fun getServices() = ServiceContainer(this, Database.connect(dataSource, PostgreSqlDialect()))

  override fun close() {
    this.dataSource.close()
  }
}
