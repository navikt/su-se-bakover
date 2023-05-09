package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
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
sealed interface Avslagsvedtak : VedtakIverksattSøknadsbehandling, Visitable<VedtakVisitor>, ErAvslag {
    override val periode: Periode
    override val behandling: IverksattSøknadsbehandling.Avslag

    companion object {
        fun fromSøknadsbehandlingMedBeregning(
            avslag: IverksattSøknadsbehandling.Avslag.MedBeregning,
            clock: Clock,
        ) = VedtakAvslagBeregning.from(
            avslag = avslag,
            clock = clock,
        )

        fun fromSøknadsbehandlingUtenBeregning(
            avslag: IverksattSøknadsbehandling.Avslag.UtenBeregning,
            clock: Clock,
        ) = VedtakAvslagVilkår.from(
            avslag = avslag,
            clock = clock,
        )
    }
}
