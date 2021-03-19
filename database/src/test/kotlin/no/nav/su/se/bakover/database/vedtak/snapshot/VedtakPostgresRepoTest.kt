package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakPostgresRepoTest {

    private val repo = VedtakssnapshotPostgresRepo(EmbeddedDatabase.instance())
    private val testDataHelper = TestDataHelper()

    @Test
    fun `insert avslag`() {
        withMigratedDb {
            val avslagUtenBeregning = testDataHelper.nyIverksattAvslagUtenBeregning()

            repo.opprettVedtakssnapshot(
                Vedtakssnapshot.Avslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    søknadsbehandling = avslagUtenBeregning,
                    avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE),
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
                )
            )
        }
    }

    @Test
    fun `insert innvilgelse`() {
        withMigratedDb {
            val (innvilget, utenKvittering) = testDataHelper.nyIverksattInnvilget()
            repo.opprettVedtakssnapshot(
                vedtakssnapshot = Vedtakssnapshot.Innvilgelse(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    søknadsbehandling = innvilget,
                    utbetaling = utenKvittering,
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
                )
            )
        }
    }
}
