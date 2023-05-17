package no.nav.su.se.bakover.common.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.output.MigrateResult
import javax.sql.DataSource

class Flyway(
    private val dataSource: DataSource,
    private val role: String? = null,
) {
    fun migrate(): MigrateResult = runMigration(dataSource, null)

    fun migrateTo(version: Int): MigrateResult = runMigration(dataSource, version)

    private fun runMigration(dataSource: DataSource, version: Int?): MigrateResult =
        Flyway.configure()
            .target(
                if (version == null) {
                    MigrationVersion.LATEST
                } else {
                    MigrationVersion.fromVersion(version.toString())
                },
            )
            .dataSource(dataSource).let {
                if (role == null) {
                    it
                } else {
                    it.initSql("SET ROLE \"$role\"")
                }
            }
            .load()
            .migrate()
}
