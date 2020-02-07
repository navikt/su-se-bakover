package no.nav.su.se.bakover

import no.nav.su.se.bakover.db.DataSourceBuilder
import no.nav.su.se.bakover.db.DataSourceBuilder.Role.Admin
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class FlywayMigrator(
        private val dataSourceBuilder: DataSourceBuilder,
        private val databaseName: String
) {
    fun migrate() {
        runMigration(dataSourceBuilder.getDataSource(Admin))
    }

    private fun runMigration(dataSource: DataSource) =
            Flyway.configure()
                    .dataSource(dataSource)
                    .initSql("SET ROLE \"$databaseName-$Admin\"")
                    .load()
                    .migrate()
}