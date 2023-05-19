package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.database.Postgres.Role
import no.nav.su.se.bakover.database.Postgres.Role.User
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Understands how to create a data source from environment variables
class Postgres(
    private val databaseConfig: ApplicationConfig.DatabaseConfig,
) {
    fun build(): AbstractDatasource {
        return when (databaseConfig) {
            is ApplicationConfig.DatabaseConfig.StaticCredentials -> NonVaultPostgres(
                jdbcUrl = databaseConfig.jdbcUrl,
                username = databaseConfig.username,
                password = databaseConfig.password,
            )
            is ApplicationConfig.DatabaseConfig.RotatingCredentials -> VaultPostgres(
                jdbcUrl = databaseConfig.jdbcUrl,
                vaultMountPath = databaseConfig.vaultMountPath,
                databaseName = databaseConfig.databaseName,
            )
        }
    }

    enum class Role {
        Admin, User, ReadOnly;

        override fun toString() = name.lowercase()
    }
}

abstract class AbstractDatasource(private val jdbcUrl: String) {
    protected val hikariConfig: HikariConfig = HikariConfig().apply {
        jdbcUrl = this@AbstractDatasource.jdbcUrl
        maximumPoolSize = 5
        connectionTimeout = 2.seconds.inWholeMilliseconds
        maxLifetime = 30.minutes.inWholeMilliseconds
    }

    abstract fun getDatasource(role: Role = User): DataSource
}

class NonVaultPostgres(jdbcUrl: String, private val username: String, private val password: String) : AbstractDatasource(jdbcUrl) {
    override fun getDatasource(role: Role) = HikariDataSource(
        hikariConfig.apply {
            username = this@NonVaultPostgres.username
            password = this@NonVaultPostgres.password
        },
    )
}

class VaultPostgres(
    jdbcUrl: String,
    private val vaultMountPath: String,
    private val databaseName: String,
) : AbstractDatasource(jdbcUrl) {
    override fun getDatasource(role: Role) = HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig,
        vaultMountPath,
        "$databaseName-$role",

    )
}
