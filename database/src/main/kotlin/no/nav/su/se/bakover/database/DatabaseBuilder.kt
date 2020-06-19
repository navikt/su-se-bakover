package no.nav.su.se.bakover.database

import javax.sql.DataSource

object DatabaseBuilder {
    fun fromEnv(env: Map<String, String>): ObjectRepo {
        val props = Properties.fromEnv(env)
        val datasource = Postgres(
                jdbcUrl = props.jdbcUrl,
                vaultMountPath = props.vaultMountPath,
                databaseName = props.databaseName,
                username = props.username,
                password = props.password
        ).build()

        Flyway(datasource.getDatasource(Postgres.Role.Admin), props.databaseName).migrate()

        return DatabaseRepo(datasource.getDatasource(Postgres.Role.User))
    }

    fun fromDatasource(dataSource: DataSource): ObjectRepo = DatabaseRepo(dataSource)

    internal data class Properties(
            val jdbcUrl: String,
            val vaultMountPath: String,
            val databaseName: String,
            val username: String,
            val password: String
    ) {
        companion object {
            fun fromEnv(env: Map<String, String>) = Properties(
                    jdbcUrl = env.getOrDefault("db.jdbcUrl", ""),
                    vaultMountPath = env.getOrDefault("db.vaultMountPath", ""),
                    databaseName = env.getOrDefault("db.name", ""),
                    username = env.getOrDefault("db.username", ""),
                    password = env.getOrDefault("db.password", ""))
        }
    }
}