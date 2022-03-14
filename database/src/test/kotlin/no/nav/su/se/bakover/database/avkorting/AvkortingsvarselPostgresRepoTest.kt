package no.nav.su.se.bakover.database.avkorting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class AvkortingsvarselPostgresRepoTest {
    @Test
    fun `avkortingsvarsel for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()
            val revurdering = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)

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
            }
        }
    }

    @Test
    fun `avkorting for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                stønadsperiode = stønadsperiode2021,
            )
            val revurdering = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)

            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes =
                Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = sak.id,
                    revurderingId = revurdering.id,
                    opprettet = fixedTidspunkt,
                    simulering = simuleringFeilutbetaling(),
                ).skalAvkortes()

            testDataHelper.sessionFactory.withTransaction {
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortingsvarsel,
                    tx = it,
                )
                testDataHelper.avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = it,
                ) shouldBe avkortingsvarsel
            }

            val nySøknadsbehandling =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart(
                    sakId = sak.id,
                    stønadsperiode = stønadsperiode2022,
                ).second

            val avkortet: Avkortingsvarsel.Utenlandsopphold.Avkortet = avkortingsvarsel.avkortet(nySøknadsbehandling.id)

            testDataHelper.sessionFactory.withTransaction {
                testDataHelper.avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet,
                    tx = it,
                )

                testDataHelper.avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = it,
                ) shouldBe avkortet
            }
        }
    }

    @Test
    fun `ikke lov å avkorte etter at det er annullert for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                stønadsperiode = stønadsperiode2021,
            )
            val revurdering = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val nySøknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart(
                sakId = sak.id,
                stønadsperiode = stønadsperiode2022,
            ).second

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

                shouldThrow<RuntimeException> {
                    testDataHelper.avkortingsvarselRepo.lagre(
                        avkortingsvarsel = avkortet,
                        tx = tx,
                    )
                }
            }
        }
    }

    @Test
    fun `henter bare utestående avkortinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering(
                stønadsperiode = stønadsperiode2021,
            )
            val revurdering1 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val revurdering2 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val revurdering3 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val nySøknadsbehandling =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart(
                    sakId = sak.id,
                    stønadsperiode = stønadsperiode2022,
                ).second

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
            val (sak, vedtak, _) = testDataHelper.persisterVedtakMedInnvilgetSøknadsbehandlingOgOversendtUtbetalingMedKvittering()
            val revurdering1 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val revurdering2 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)
            val revurdering3 = testDataHelper.persisterRevurderingOpprettet(innvilget = vedtak, vedtak.periode)

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
