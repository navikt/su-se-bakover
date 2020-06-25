package no.nav.su.se.bakover.database

import javax.sql.DataSource

object DatabaseBuilder {
    fun build(): ObjectRepo {
        val env = System.getenv()
        val databaseName = env.getOrDefault("DATABASE_NAME", "supstonad-db-local")
        val datasource = Postgres(
            jdbcUrl = env.getOrDefault("DATABASE_JDBC_URL", "jdbc:postgresql://localhost:5432/supstonad-db-local"),
            vaultMountPath = env.getOrDefault("VAULT_MOUNTPATH", ""),
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
