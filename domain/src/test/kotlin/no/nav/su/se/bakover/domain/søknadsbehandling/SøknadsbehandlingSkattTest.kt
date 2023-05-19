package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.skatt.EksternGrunnlagSkattRequest
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simuleringNy
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.underkjentSøknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SøknadsbehandlingSkattTest {

    @Nested
    inner class KanIkkeLeggeTilSkattegrunnlag {
        @Test
        fun `tilstand tilAttestering`() {
            søknadsbehandlingTilAttesteringInnvilget().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()
        }

        @Test
        fun `tilstand iverksatt`() {
            søknadsbehandlingIverksattInnvilget().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()
        }

        @Test
        fun `tilstand lukket`() {
            søknadsbehandlingTrukket().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()
        }
    }

    @Nested
    inner class KanLeggeTil {
        @Test
        fun `tilstand vilkårsvurdert med eps`() {
            søknadsbehandlingVilkårsvurdertUavklart().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), nySkattegrunnlag()),
            ).shouldBeRight()
        }

        @Test
        fun `tilstand beregnet - kan legge til dersom hentet fra før, ellers left`() {
            beregnetSøknadsbehandling().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()

            søknadsbehandlingVilkårsvurdertInnvilget().second
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null))
                .getOrFail()
                .beregn(saksbehandler, "", fixedClock, satsFactoryTestPåDato())
                .getOrFail()
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null))
                .shouldBeRight()
        }

        @Test
        fun `tilstand simulert - kan legge til dersom hentet fra før, ellers left`() {
            simulertSøknadsbehandling().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()

            søknadsbehandlingVilkårsvurdertInnvilget().second
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null))
                .getOrFail()
                .beregn(saksbehandler, "", fixedClock, satsFactoryTestPåDato())
                .getOrFail()
                .simuler(
                    saksbehandler,
                    fixedClock,
                ) { _, _ -> simuleringNy().right() }
                .getOrFail()
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null))
                .shouldBeRight()
        }

        @Test
        fun `tilstand underkjent - kan legge til dersom hentet fra før, ellers left`() {
            underkjentSøknadsbehandling().second.leggTilSkatt(
                EksternGrunnlagSkattRequest(nySkattegrunnlag(), null),
            ).shouldBeLeft()

            søknadsbehandlingVilkårsvurdertInnvilget().second
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null))
                .getOrFail()
                .beregn(saksbehandler, "", fixedClock, satsFactoryTestPåDato())
                .getOrFail()
                .simuler(saksbehandler, fixedClock) { _, _ -> simuleringNy().right() }
                .getOrFail()
                .tilAttestering(saksbehandler, "", fixedClock)
                .getOrFail()
                .tilUnderkjent(
                    Attestering.Underkjent(
                        NavIdentBruker.Attestant("attestanten"),
                        enUkeEtterFixedTidspunkt,
                        Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        "Skal underkjenne for å kunne hente ny skattegrunnlag",
                    ),
                )
                .leggTilSkatt(EksternGrunnlagSkattRequest(nySkattegrunnlag(), null)).shouldBeRight()
        }
    }
}
