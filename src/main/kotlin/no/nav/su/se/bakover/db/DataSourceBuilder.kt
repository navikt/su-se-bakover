package no.nav.su.se.bakover.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.db.DataSourceBuilder.Role
import no.nav.su.se.bakover.db.DataSourceBuilder.Role.User
import no.nav.su.se.bakover.getProperty
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import javax.sql.DataSource

// Understands how to create a data source from environment variables
@KtorExperimentalAPI
class DataSourceBuilder(private val env: ApplicationConfig) {
    fun build(): AbstractDatasource {
        val vaultMountPath: String = env.getProperty("db.vaultMountPath")
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

@KtorExperimentalAPI
abstract class AbstractDatasource(env: ApplicationConfig) {
    private val jdbcUrl: String = env.getProperty("db.jdbcUrl")
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

@KtorExperimentalAPI
class EmbeddedPostgres(env: ApplicationConfig) : AbstractDatasource(env) {
    private val username: String = env.getProperty("db.username")
    private val password: String = env.getProperty("db.password")
    override fun getDatasource(role: Role) = HikariDataSource(hikariConfig.apply {
        username = this@EmbeddedPostgres.username
        password = this@EmbeddedPostgres.password
    })

}

@KtorExperimentalAPI
class VaultPostgres(env: ApplicationConfig) : AbstractDatasource(env) {
    private val vaultMountPath: String = env.getProperty("db.vaultMountPath")
    private val databaseName: String = env.getProperty("db.name")
    override fun getDatasource(role: Role) = HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            vaultMountPath,
            "$databaseName-$role"

    )

}
