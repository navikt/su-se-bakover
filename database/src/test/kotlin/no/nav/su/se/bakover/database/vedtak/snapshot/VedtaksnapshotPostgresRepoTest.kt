package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtaksnapshotPostgresRepoTest {

    @Test
    fun `insert avslag`() {
        withMigratedDb { dataSource ->
            val repo = VedtakssnapshotPostgresRepo(dataSource)
            val testDataHelper = TestDataHelper(dataSource)
            val avslagUtenBeregning = testDataHelper.nyIverksattAvslagUtenBeregning()

            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Avslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    søknadsbehandling = avslagUtenBeregning,
                    avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE),
                )
            )
        }
    }

    @Test
    fun `insert innvilgelse`() {
        withMigratedDb { dataSource ->
            val repo = VedtakssnapshotPostgresRepo(dataSource)
            val testDataHelper = TestDataHelper(dataSource)
            val (innvilget, utenKvittering) = testDataHelper.nyIverksattInnvilget()
            repo.opprettVedtakssnapshot(
                vedtakssnapshot = Vedtakssnapshot.Innvilgelse(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    søknadsbehandling = innvilget,
                    utbetaling = utenKvittering,
                )
            )
        }
    }
}
