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

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = UUID.randomUUID(),
                behandlingId = UUID.randomUUID(),
                avkortingsvarsel = Avkortingsvarsel.Ingen,
            )

            testDataHelper.avkortingsvarselRepo.hentForBehandling(
                UUID.randomUUID(),
            ) shouldBe Avkortingsvarsel.Ingen
        }
    }

    @Test
    fun `avkortingsvarsel for utenlandsopphold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val sak = testDataHelper.nySakMedNySøknad()
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val revurdering = testDataHelper.nyRevurdering(innvilget = vedtak, vedtak.periode)

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
            )

            testDataHelper.avkortingsvarselRepo.hentForBehandling(
                revurdering.id,
            ) shouldBe avkortingsvarsel

            val skalAvkortes = avkortingsvarsel.skalAvkortes()

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = sak.id,
                behandlingId = revurdering.id,
                avkortingsvarsel = skalAvkortes,
            )

            testDataHelper.avkortingsvarselRepo.hentForBehandling(
                revurdering.id,
            ) shouldBe skalAvkortes

            val avkortet = skalAvkortes.avkortet()

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = sak.id,
                behandlingId = revurdering.id,
                avkortingsvarsel = avkortet,
            )

            testDataHelper.avkortingsvarselRepo.hentForBehandling(
                revurdering.id,
            ) shouldBe avkortet
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

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = sak.id,
                behandlingId = revurdering1.id,
                avkortingsvarsel = opprettet,
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

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = sak.id,
                behandlingId = revurdering2.id,
                avkortingsvarsel = skalAvkortes,
            )

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

            testDataHelper.avkortingsvarselRepo.lagre(
                sakId = sak.id,
                behandlingId = revurdering3.id,
                avkortingsvarsel = avkortet,
            )

            testDataHelper.avkortingsvarselRepo.hentUteståendeAvkortinger(sak.id) shouldBe listOf(skalAvkortes)
        }
    }
}
