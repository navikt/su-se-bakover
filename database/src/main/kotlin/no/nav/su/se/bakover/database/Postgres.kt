package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.database.Postgres.Role
import no.nav.su.se.bakover.database.Postgres.Role.User
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import javax.sql.DataSource

// Understands how to create a data source from environment variables
internal class Postgres(
        private val jdbcUrl: String,
        private val vaultMountPath: String,
        private val databaseName: String,
        private val username: String,
        private val password: String
) {
    fun build(): AbstractDatasource {
        return when (vaultMountPath.let { it != "" }) {
            true -> VaultPostgres(jdbcUrl, vaultMountPath, databaseName)
            else -> NonVaultPostgres(jdbcUrl, username, password)
        }
    }

    enum class Role {
        Admin, User, ReadOnly;

        override fun toString() = name.toLowerCase()
    }
}

internal abstract class AbstractDatasource(private val jdbcUrl: String) {
    protected val hikariConfig: HikariConfig = HikariConfig().apply {
        jdbcUrl = this@AbstractDatasource.jdbcUrl
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    internal abstract fun getDatasource(role: Role = User): DataSource
}

internal class NonVaultPostgres(jdbcUrl: String, private val username: String, private val password: String) : AbstractDatasource(jdbcUrl) {
    override fun getDatasource(role: Role) = HikariDataSource(hikariConfig.apply {
        username = this@NonVaultPostgres.username
        password = this@NonVaultPostgres.password
    })

}

internal class VaultPostgres(private val jdbcUrl: String, private val vaultMountPath: String, private val databaseName: String) : AbstractDatasource(jdbcUrl) {
    override fun getDatasource(role: Role) = HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            vaultMountPath,
            "$databaseName-$role"

    )
}
