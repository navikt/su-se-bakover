package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

/**
 * Et avslagsvedtak fører ikke til endring i ytelsen.
 * Derfor vil et avslagsvedtak sin "stønadsperiode" kunne overlappe tidligere avslagsvedtak og andre vedtak som påvirker ytelsen.<br>
 *
 * [GjeldendeVedtaksdata] tar ikke hensyn til avslagsvedtak per tidspunkt, siden de ikke påvirker selve ytelsen.
 * Så hvis vi på et tidspunkt skal kunne revurdere/omgjøre disse vedtakene, så kan man ikke blindt arve [VedtakSomKanRevurderes].
 */
sealed interface Avslagsvedtak : Stønadsvedtak, Visitable<VedtakVisitor>, ErAvslag {
    override val periode: Periode
    override val behandling: Søknadsbehandling.Iverksatt.Avslag

    companion object {
        fun fromSøknadsbehandlingMedBeregning(
            avslag: Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
            clock: Clock,
        ) = VedtakAvslagBeregning.from(
            avslag = avslag,
            clock = clock,
        )

        fun fromSøknadsbehandlingUtenBeregning(
            avslag: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
            clock: Clock,
        ) = VedtakAvslagVilkår.from(
            avslag = avslag,
            clock = clock,
        )
    }
}
