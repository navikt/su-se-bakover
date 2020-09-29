package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
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
            avstemmingRepo = AvstemmingPostgresRepo(userDatastore),
            utbetalingRepo = UtbetalingPostgresRepo(userDatastore)
        )
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        val objectRepo = DatabaseRepo(embeddedDatasource)
        return DatabaseRepos(
            objectRepo = objectRepo,
            avstemmingRepo = AvstemmingPostgresRepo(embeddedDatasource),
            utbetalingRepo = UtbetalingPostgresRepo(embeddedDatasource)
        )
    }
}

data class DatabaseRepos(
    val objectRepo: ObjectRepo,
    val avstemmingRepo: AvstemmingRepo,
    val utbetalingRepo: UtbetalingRepo
)
