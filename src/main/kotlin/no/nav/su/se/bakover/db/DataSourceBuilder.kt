package no.nav.su.se.bakover.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.db.DataSourceBuilder.Role
import no.nav.su.se.bakover.db.DataSourceBuilder.Role.User
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import javax.sql.DataSource

// Understands how to create a data source from environment variables
class DataSourceBuilder(private val env: Map<String, String>) {
    fun build(): AbstractDatasource {
        val vaultMountPath: String = env["VAULT_MOUNTPATH"] ?: error("VAULT_MOUNTPATH påkrevd (bruk tom string \"\" for embedded postgres)")
        return when (vaultMountPath.let { it != "" }) {
            true -> VaultPostgres(env)
            else -> EmbeddedPostgres(env)
        }
    }

    enum class Role {
        Admin, User, ReadOnly;

        override fun toString() = name.toLowerCase()
    }
}

abstract class AbstractDatasource(env: Map<String, String>) {
    private val jdbcUrl: String = env["DATABASE_JDBC_URL"] ?: error("DATABASE_JDBC_URL er påkrevd")
    protected val hikariConfig: HikariConfig = HikariConfig().apply {
        jdbcUrl = this@AbstractDatasource.jdbcUrl
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    abstract fun getDatasource(role: Role = User): DataSource
}

class EmbeddedPostgres(env: Map<String, String>) : AbstractDatasource(env) {
    private val username: String = env["DATABASE_USERNAME"] ?: error("DATABASE_USERNAME påkrevd for embedded postgres")
    private val password: String = env["DATABASE_PASSWORD"] ?: error("DATABASE_PASSWORD påkrevd for embedded postgres")
    override fun getDatasource(role: Role) = HikariDataSource(hikariConfig.apply {
        username = this@EmbeddedPostgres.username
        password = this@EmbeddedPostgres.password
    })

}

class VaultPostgres(env: Map<String, String>) : AbstractDatasource(env) {
    private val vaultMountPath: String = env["VAULT_MOUNTPATH"] ?: error("VAULT_MOUNTPATH påkrevd for integrasjon mot vault")
    private val databaseName: String = env["DATABASE_NAME"] ?: error("DATABASE_NAME pågrevd ved integrasjon mot vault")
    override fun getDatasource(role: Role) = HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            vaultMountPath,
            "$databaseName-$role"

    )

}
