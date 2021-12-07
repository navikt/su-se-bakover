package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Avkortingsvarsel
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
                    sakId = UUID.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    avkortingsvarsel = Avkortingsvarsel.Ingen,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForBehandling(
                    behandlingId = UUID.randomUUID(),
                    session = tx,
                ) shouldBe Avkortingsvarsel.Ingen
            }
        }
    }

    @Test
    fun `avkortingsvarsel for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedNySøknad()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)

            testDataHelper.sessionFactory.withTransaction { tx ->

                val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    simulering = simuleringFeilutbetaling(),
                    feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 12345,
                        virkningstidspunkt = 1.juni(2021),
                        uføregrad = Uføregrad.parse(50),
                    ),
                )

                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering.id,
                    avkortingsvarsel = avkortingsvarsel,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForBehandling(
                    behandlingId = revurdering.id,
                    session = tx,
                ) shouldBe avkortingsvarsel

                val skalAvkortes = avkortingsvarsel.skalAvkortes()

                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering.id,
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForBehandling(
                    behandlingId = revurdering.id,
                    session = tx,
                ) shouldBe skalAvkortes

                val avkortet = skalAvkortes.avkortet()

                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering.id,
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.hentForBehandling(
                    behandlingId = revurdering.id,
                    session = tx,
                ) shouldBe avkortet
            }
        }
    }

    @Test
    fun `henter bare utestående avkortinger for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedNySøknad()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering1 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering2 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)
            val revurdering3 = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)

            val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                simulering = simuleringFeilutbetaling(),
                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 12345,
                    virkningstidspunkt = 1.juni(2021),
                    uføregrad = Uføregrad.parse(50),
                ),
            )

            val skalAvkortes = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                simulering = simuleringFeilutbetaling(),
                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 12345,
                    virkningstidspunkt = 1.juni(2021),
                    uføregrad = Uføregrad.parse(50),
                ),
            ).skalAvkortes()

            val avkortet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                simulering = simuleringFeilutbetaling(),
                feilutbetalingslinje = Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 12345,
                    virkningstidspunkt = 1.juni(2021),
                    uføregrad = Uføregrad.parse(50),
                ),
            ).skalAvkortes().avkortet()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering1.id,
                    avkortingsvarsel = opprettet,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering2.id,
                    avkortingsvarsel = skalAvkortes,
                    tx = tx,
                )

                testDataHelper.avkortingsvarselRepo.lagre(
                    sakId = sak.id,
                    behandlingId = revurdering3.id,
                    avkortingsvarsel = avkortet,
                    tx = tx,
                )
            }

            testDataHelper.avkortingsvarselRepo.hentUteståendeAvkortinger(sak.id) shouldBe listOf(skalAvkortes)
        }
    }
}
