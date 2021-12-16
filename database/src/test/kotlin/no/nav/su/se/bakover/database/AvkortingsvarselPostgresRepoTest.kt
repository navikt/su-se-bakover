package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingsvarselPostgresRepoTest {
    @Test
    fun `ingen avkortingsvarsel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = UUID.randomUUID(),
                    avkortingsvarsel = Avkortingsvarsel.Ingen,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                    revurderingId = UUID.randomUUID(),
                    session = tx,
                ) shouldBe Avkortingsvarsel.Ingen
            }
        }
    }

    @Test
    fun `avkortingsvarsel for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val nySøknadsbehandling = testDataHelper.nySøknadsbehandling(sak = sak)

            testDataHelper.sessionFactory.withTransaction { tx ->

                val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = sak.id,
                    revurderingId = revurdering.id,
                    opprettet = fixedTidspunkt,
                    simulering = simuleringFeilutbetaling(),
                )

                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering.id,
                    avkortingsvarsel = avkortingsvarsel,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                    revurderingId = revurdering.id,
                    session = tx,
                ) shouldBe avkortingsvarsel

                val skalAvkortes = avkortingsvarsel.skalAvkortes()

                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering.id,
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                    revurderingId = revurdering.id,
                    session = tx,
                ) shouldBe skalAvkortes

                val avkortet = skalAvkortes.avkortet(nySøknadsbehandling.id)

                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering.id,
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForRevurdering(
                    revurderingId = revurdering.id,
                    session = tx,
                ) shouldBe avkortet

                testDataHelper.avkortingsvarselRepo.hentFullførtAvkorting(
                    søknadsbehandlingId = nySøknadsbehandling.id,
                    session = tx,
                ) shouldBe avkortet
            }
        }
    }

    @Test
    fun `henter bare utestående avkortinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering1 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering2 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering3 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val nySøknadsbehandling = testDataHelper.nySøknadsbehandling(sak = sak)

            val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = UUID.randomUUID(),
                sakId = sak.id,
                revurderingId = revurdering1.id,
                opprettet = fixedTidspunkt,
                simulering = simuleringFeilutbetaling(),
            )

            val skalAvkortes = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering2.id)
            val avkortet = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering3.id)

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering1.id,
                    avkortingsvarsel = opprettet,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering2.id,
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering2.id,
                    avkortingsvarsel = skalAvkortes.skalAvkortes(),
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering3.id,
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    revurderingId = revurdering3.id,
                    avkortingsvarsel = avkortet.skalAvkortes().avkortet(nySøknadsbehandling.id),
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.hentUteståendeAvkorting(
                    sakId = sak.id,
                    session = tx,
                ) shouldBe skalAvkortes.skalAvkortes()
            }
        }
    }
}
