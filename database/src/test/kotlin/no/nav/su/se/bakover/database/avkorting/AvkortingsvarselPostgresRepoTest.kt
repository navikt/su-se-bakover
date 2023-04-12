package no.nav.su.se.bakover.database.avkorting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2022
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class AvkortingsvarselPostgresRepoTest {
    @Test
    fun `avkortingsvarsel for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, revurdering) = testDataHelper.persisterRevurderingOpprettet()
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo

            testDataHelper.sessionFactory.withTransaction { tx ->

                val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = sak.id,
                    revurderingId = revurdering.id,
                    opprettet = fixedTidspunkt,
                    simulering = simuleringFeilutbetaling(),
                ).skalAvkortes()

                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortingsvarsel,
                    tx = tx,
                )

                avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = tx,
                ) shouldBe avkortingsvarsel

                val annullert = avkortingsvarsel.annuller(revurdering.id)

                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert,
                    tx = tx,
                )

                avkortingsvarselRepo.hent(
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
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo
            val (sak, revurdering) = testDataHelper.persisterRevurderingOpprettet()

            val avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes =
                Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = sak.id,
                    revurderingId = revurdering.id,
                    opprettet = fixedTidspunkt,
                    simulering = simuleringFeilutbetaling(),
                ).skalAvkortes()

            testDataHelper.sessionFactory.withTransaction {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortingsvarsel,
                    tx = it,
                )
                avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = it,
                ) shouldBe avkortingsvarsel
            }

            val nySøknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second

            val avkortet: Avkortingsvarsel.Utenlandsopphold.Avkortet = avkortingsvarsel.avkortet(nySøknadsbehandling.id)

            testDataHelper.sessionFactory.withTransaction {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet,
                    tx = it,
                )

                avkortingsvarselRepo.hent(
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
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo
            val (sak, revurdering) = testDataHelper.persisterRevurderingOpprettet()
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

                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortingsvarsel,
                    tx = tx,
                )

                avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = tx,
                ) shouldBe avkortingsvarsel

                val annullert = avkortingsvarsel.annuller(revurdering.id)

                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert,
                    tx = tx,
                )

                avkortingsvarselRepo.hent(
                    id = avkortingsvarsel.id,
                    session = tx,
                ) shouldBe annullert

                val avkortet = avkortingsvarsel.avkortet(nySøknadsbehandling.id)

                shouldThrow<RuntimeException> {
                    avkortingsvarselRepo.lagre(
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
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo
            val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val revurdering1 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second
            val revurdering2 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second
            val revurdering3 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second

            val (_, nySøknadsbehandling) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart { (sak, søknad) ->
                nySøknadsbehandlingMedStønadsperiode(
                    sakOgSøknad = sak to søknad,
                    stønadsperiode = stønadsperiode2022,
                ).let {
                    it.first to it.second
                }
            }

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
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert,
                    tx = tx,
                )
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkortet.avkortet(nySøknadsbehandling.id),
                    tx = tx,
                )
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = annullert.annuller(revurdering3.id),
                    tx = tx,
                )
            }

            avkortingsvarselRepo.hentUtestående(
                sakId = sak.id,
            ) shouldBe skalAvkortes
        }
    }

    @Test
    fun `kaster exception hvis det finnes flere utestående avkortinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val avkortingsvarselRepo = testDataHelper.avkortingsvarselRepo as AvkortingsvarselPostgresRepo
            val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val revurdering1 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second
            val revurdering2 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second
            val revurdering3 = testDataHelper.persisterRevurderingOpprettet(sakOgVedtak = (sak to vedtak)).second

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
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes1,
                    tx = tx,
                )
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = skalAvkortes2,
                    tx = tx,
                )
                assertThrows<IllegalStateException> {
                    avkortingsvarselRepo.hentUteståendeAvkorting(
                        sakId = sak.id,
                        session = tx,
                    )
                }
            }
        }
    }
}
