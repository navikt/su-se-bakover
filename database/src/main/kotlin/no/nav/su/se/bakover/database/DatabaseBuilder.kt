package no.nav.su.se.bakover.database

import javax.sql.DataSource
import no.nav.su.se.bakover.common.Config

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
            objectRepo = objectRepo
        )
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        val objectRepo = DatabaseRepo(embeddedDatasource)
        return DatabaseRepos(
            objectRepo = objectRepo
        )
    }
}

data class DatabaseRepos(
    val objectRepo: ObjectRepo
)
