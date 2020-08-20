package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(): ObjectRepo {
        val databaseName = Config.databaseName
        val datasource = Postgres(
            jdbcUrl = Config.jdbcUrl,
            vaultMountPath = Config.vaultMountPath,
            databaseName = databaseName,
            username = "user",
            password = "pwd"
        ).build()

        Flyway(datasource.getDatasource(Postgres.Role.Admin), databaseName).migrate()

        return DatabaseRepo(datasource.getDatasource(Postgres.Role.User))
    }

    fun build(embeddedDatasource: DataSource): ObjectRepo {
        Flyway(embeddedDatasource, "postgres").migrate()
        return DatabaseRepo(embeddedDatasource)
    }
}
