package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.database.behandlinger.stopp.StoppbehandlingJdbcRepo
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingRepo
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(): DatabaseRepos {
        val databaseName = Config.databaseName
        val abstractDatasource = Postgres(
            jdbcUrl = Config.jdbcUrl,
            vaultMountPath = Config.vaultMountPath,
            databaseName = databaseName,
            username = "user",
            password = "pwd"
        ).build()

        Flyway(abstractDatasource.getDatasource(Postgres.Role.Admin), databaseName).migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        val objectRepo = DatabaseRepo(userDatastore)
        return DatabaseRepos(
            objectRepo = objectRepo,
            stoppbehandlingRepo = StoppbehandlingJdbcRepo(userDatastore, objectRepo)
        )
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        val objectRepo = DatabaseRepo(embeddedDatasource)
        return DatabaseRepos(
            objectRepo = objectRepo,
            stoppbehandlingRepo = StoppbehandlingJdbcRepo(embeddedDatasource, objectRepo)
        )
    }
}

data class DatabaseRepos(
    val objectRepo: ObjectRepo,
    val stoppbehandlingRepo: StoppbehandlingRepo
)
