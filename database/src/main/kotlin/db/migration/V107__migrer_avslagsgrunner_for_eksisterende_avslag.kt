package db.migration

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.Clock

internal class V107__migrer_avslagsgrunner_for_eksisterende_avslag : BaseJavaMigration() {
    override fun migrate(context: Context?) {
        val statement = context!!.connection.createStatement()

        val vedtakRepo = DatabaseBuilder.buildInternal(
            dataSource = context.configuration.dataSource,
            dbMetrics = object : DbMetrics {
                override fun <T> timeQuery(label: String, block: () -> T): T {
                    return block()
                }
            },
            clock = Clock.systemUTC(),
            satsFactory = SatsFactoryForSupplerendeStønad()
        ).vedtakRepo

        val ps = context.connection.prepareStatement("update vedtak set avslagsgrunner = to_json(?::json) where id = ?")

        vedtakRepo.hentAlle()
            .filterIsInstance<Avslagsvedtak>()
            .map { it.id to it.avslagsgrunner }
            .forEach { (id, avslagsgrunner) ->
                ps.setString(1, avslagsgrunner.serialize())
                ps.setObject(2, id)
                ps.addBatch()
            }

        ps.executeBatch()
        ps.close()
        statement.close()
    }
}
