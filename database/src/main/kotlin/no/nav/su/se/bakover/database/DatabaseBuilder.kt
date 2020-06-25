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
            fun fromEnv(env: Map<String, String>) = env.validateKeys()
                    .let {
                        Properties(
                                jdbcUrl = env.getValue("db.jdbcUrl"),
                                vaultMountPath = env.getValue("db.vaultMountPath"),
                                databaseName = env.getValue("db.name"),
                                username = env.getValue("db.username"),
                                password = env.getValue("db.password"))
                    }
        }
    }
}

internal fun Map<String, String>.validateKeys() {
    require(containsKey("db.jdbcUrl")) { "Missing key:db.jdbcUrl" }
    require(containsKey("db.vaultMountPath")) { "Missing key:db.vaultMountPath" }
    require(containsKey("db.name")) { "Missing key:db.name" }
    require(containsKey("db.username")) { "Missing key:db.username" }
    require(containsKey("db.password")) { "Missing key:db.password" }
}
