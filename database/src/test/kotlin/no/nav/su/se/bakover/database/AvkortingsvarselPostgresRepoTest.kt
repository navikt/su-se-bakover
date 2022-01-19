package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class AvkortingsvarselPostgresRepoTest {
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
                ).skalAvkortes()

                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortingsvarsel,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = tx,
                ) shouldBe avkortingsvarsel

                val annullert = avkortingsvarsel.annuller(revurdering.id)

                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = tx,
                ) shouldBe annullert

                val avkortet = avkortingsvarsel.avkortet(nySøknadsbehandling.id)

                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
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
                .skalAvkortes()
            val avkortet = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering3.id)
                .skalAvkortes()
            val annullert = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering3.id)
                .skalAvkortes()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert,
                    tx = tx,
                )
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet.avkortet(nySøknadsbehandling.id),
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert.annuller(revurdering3.id),
                    tx = tx,
                )
            }

            testDataHelper.avkortingsvarselRepo.hentUtestående(
                sakId = sak.id,
            ) shouldBe skalAvkortes
        }
    }

    @Test
    fun `kaster exception hvis det finnes flere utestående avkortinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering1 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering2 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering3 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)

            val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = UUID.randomUUID(),
                sakId = sak.id,
                revurderingId = revurdering1.id,
                opprettet = fixedTidspunkt,
                simulering = simuleringFeilutbetaling(),
            )

            val skalAvkortes1 = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering2.id).skalAvkortes()
            val skalAvkortes2 = opprettet.copy(id = UUID.randomUUID(), revurderingId = revurdering3.id).skalAvkortes()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes1,
                    tx = tx,
                )
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes2,
                    tx = tx,
                )
                assertThrows<IllegalStateException> {
                    testDataHelper.avkortingsvarselRepo.hentUteståendeAvkorting(
                        sakId = sak.id,
                        session = tx,
                    )
                }
            }
        }
    }
}
