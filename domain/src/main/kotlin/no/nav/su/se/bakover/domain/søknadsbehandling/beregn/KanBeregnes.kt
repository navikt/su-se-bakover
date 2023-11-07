@file:Suppress("PackageDirectoryMismatch")
// Denne må ligge i samme pakke som [Søknadsbehandling], men den trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import java.time.Clock

/**
 * Markerer at en søknadsbehandling kan beregnes.
 */
sealed interface KanBeregnes : Søknadsbehandling {

    override val aldersvurdering: Aldersvurdering

    fun beregn(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        require(!grunnlagsdataOgVilkårsvurderinger.harAvkortingsfradrag()) {
            "Vi støtter ikke lenger å beregne med avkortingsfradrag. For sakId ${this.sakId}"
        }
        val beregningStrategyFactory = BeregningStrategyFactory(
            clock = clock,
            satsFactory = satsFactory,
        )

        val beregning = beregningStrategyFactory.beregn(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = begrunnelse,
            sakstype = this.sakstype,
        )
        val nySøknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
            saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                tidspunkt = Tidspunkt.now(clock),
                saksbehandler = nySaksbehandler,
                handling = SøknadsbehandlingsHandling.Beregnet,
            ),
        )
        return when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
            is AvslagGrunnetBeregning.Ja -> tilAvslåttBeregning(
                saksbehandler = nySaksbehandler,
                beregning = beregning,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikk,
            )

            AvslagGrunnetBeregning.Nei -> {
                tilInnvilgetBeregning(
                    saksbehandler = nySaksbehandler,
                    beregning = beregning,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikk,
                )
            }
        }.right()
    }

    private fun tilAvslåttBeregning(
        saksbehandler: NavIdentBruker.Saksbehandler,
        beregning: Beregning,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
    ): BeregnetSøknadsbehandling.Avslag {
        return BeregnetSøknadsbehandling.Avslag(
            id = this.id,
            opprettet = this.opprettet,
            sakId = this.sakId,
            saksnummer = this.saksnummer,
            søknad = this.søknad,
            oppgaveId = this.oppgaveId,
            fnr = this.fnr,
            beregning = beregning,
            fritekstTilBrev = this.fritekstTilBrev,
            aldersvurdering = this.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            attesteringer = this.attesteringer,
            søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            sakstype = this.sakstype,
            saksbehandler = saksbehandler,
        )
    }

    private fun tilInnvilgetBeregning(
        saksbehandler: NavIdentBruker.Saksbehandler,
        beregning: Beregning,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
    ): BeregnetSøknadsbehandling.Innvilget {
        return BeregnetSøknadsbehandling.Innvilget(
            id = this.id,
            opprettet = this.opprettet,
            sakId = this.sakId,
            saksnummer = this.saksnummer,
            søknad = this.søknad,
            oppgaveId = this.oppgaveId,
            fnr = this.fnr,
            beregning = beregning,
            fritekstTilBrev = this.fritekstTilBrev,
            aldersvurdering = this.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            attesteringer = this.attesteringer,
            søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            sakstype = this.sakstype,
            saksbehandler = saksbehandler,
        )
    }
}
