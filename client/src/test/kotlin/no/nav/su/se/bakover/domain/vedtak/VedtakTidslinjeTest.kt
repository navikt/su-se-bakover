package no.nav.su.se.bakover.domain.vedtak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VedtakTidslinjeTest {
    val periode = Periode.create(1.januar(2021), 31.desember(2021))
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    val søknadsbehandling = Vedtak.EndringIYtelse(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock),
        behandling = mock(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        saksbehandler = mock(),
        attestant = mock(),
        journalføringOgBrevdistribusjon = mock(),
        periode = periode,
        beregning = mock(),
        simulering = mock(),
        utbetalingId = mock(),
        vedtakType = mock()
    )

    @Test
    fun `revurdering av søknadsbehandling skal ge riktig tidslinje`(){
        val revurdering = Vedtak.EndringIYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            behandling = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            saksbehandler = mock(),
            attestant = mock(),
            journalføringOgBrevdistribusjon = mock(),
            periode = Periode.create(1.juni(2021), 31.desember(2021)),
            beregning = mock(),
            simulering = mock(),
            utbetalingId = mock(),
            vedtakType = mock()
        )

        val actual = Tidslinje(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            objekter = listOf(søknadsbehandling, revurdering),
            clock = fixedClock
        ).tidslinje

        actual shouldBe listOf(
            søknadsbehandling.copy(
                id = søknadsbehandling.id,
                periode = Periode.create(søknadsbehandling.periode.fraOgMed, revurdering.periode.fraOgMed.minusDays(1))
            ),
            revurdering.copy(
                id = revurdering.id,
            )
        )
    }
}
