package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.database.Postgres.Role
import no.nav.su.se.bakover.database.Postgres.Role.User
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Postgres(
    private val databaseConfig: ApplicationConfig.DatabaseConfig,
) {
    fun build(): AbstractDatasource {
        return when (databaseConfig) {
            is ApplicationConfig.DatabaseConfig.StaticCredentials -> PostgresDataSource(
                jdbcUrl = databaseConfig.jdbcUrl,
                username = databaseConfig.username,
                password = databaseConfig.password,
            )
        }
    }

    enum class Role {
        Admin,
        User,
        ReadOnly,
        ;

        override fun toString() = name.lowercase()
    }
}

val defaultConnectionPoolSizeForApp = 15

abstract class AbstractDatasource(private val jdbcUrl: String, val maximumPoolSizeOverride: Int = defaultConnectionPoolSizeForApp) {
    protected val hikariConfig: HikariConfig = HikariConfig().apply {
        jdbcUrl = this@AbstractDatasource.jdbcUrl
        maximumPoolSize = maximumPoolSizeOverride
        connectionTimeout = 3.seconds.inWholeMilliseconds
        maxLifetime = 30.minutes.inWholeMilliseconds
    }

    abstract fun getDatasource(role: Role = User): DataSource
}

class PostgresDataSource(jdbcUrl: String, private val username: String, private val password: String) : AbstractDatasource(jdbcUrl) {
    override fun getDatasource(role: Role) = HikariDataSource(
        hikariConfig.apply {
            username = this@PostgresDataSource.username
            password = this@PostgresDataSource.password
        },
    )
}
