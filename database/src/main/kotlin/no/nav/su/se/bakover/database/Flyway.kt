package no.nav.su.se.bakover.database

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import javax.sql.DataSource

internal class Flyway(
    private val dataSource: DataSource,
    private val role: String? = null,
) {
    fun migrate(): MigrateResult = runMigration(dataSource)

    private fun runMigration(dataSource: DataSource): MigrateResult =
        Flyway.configure()
            .dataSource(dataSource).let {
                if (role == null)
                    it
                else
                    it.initSql("SET ROLE \"$role\"")
            }
            .load()
            .migrate()
}
