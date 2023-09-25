package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.AvkortingsopphørUtenUtbetalingslinjer
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørsperiodeForUtbetalinger
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private val log = LoggerFactory.getLogger("RevurderingTilAttestering.kt")

sealed class RevurderingTilAttestering : Revurdering() {
    abstract override val beregning: Beregning
    abstract override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering

    abstract override val avkorting: AvkortingVedRevurdering.Håndtert
    abstract override val brevvalgRevurdering: BrevvalgRevurdering.Valgt
    abstract val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

    override fun erÅpen() = true

    abstract fun tilIverksatt(
        attestant: NavIdentBruker.Attestant,
        uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
        clock: Clock,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : RevurderingTilAttestering() {

        override val erOpphørt = false

        override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
            clock: Clock,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Innvilget> = validerTilIverksettOvergang(
            attestant = attestant,
            uteståendeAvkortingPåSak = uteståendeAvkortingPåSak,
            saksbehandler = saksbehandler,
            avkorting = avkorting,
        ).map {
            IverksattRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                attesteringer = attesteringer.leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ),
                avkorting = avkorting.iverksett(id),
                tilbakekrevingsbehandling = tilbakekrevingsbehandling.fullførBehandling(),
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )
        }

        override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = emptyList()
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : RevurderingTilAttestering() {
        override val erOpphørt = true

        override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

        val opphørsperiodeForUtbetalinger: Either<AvkortingsopphørUtenUtbetalingslinjer, Periode> =
            OpphørsperiodeForUtbetalinger(this).map { it.value }

        override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        /**
         * Hvis alle månedene i en revurdering fører til avkorting, skal vi ikke sende utbetalingslinjer.
         */
        fun erAlleMånedeneAvkorting(): Boolean {
            return opphørsperiodeForUtbetalinger is Either.Left<AvkortingsopphørUtenUtbetalingslinjer>
        }

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
            clock: Clock,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Opphørt> {
            return validerTilIverksettOvergang(
                attestant = attestant,
                uteståendeAvkortingPåSak = uteståendeAvkortingPåSak,
                saksbehandler = saksbehandler,
                avkorting = avkorting,
            ).map {
                IverksattRevurdering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    oppdatert = oppdatert,
                    tilRevurdering = tilRevurdering,
                    vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer.leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant,
                            Tidspunkt.now(clock),
                        ),
                    ),
                    avkorting = avkorting.iverksett(id),
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling.fullførBehandling(),
                    sakinfo = sakinfo,
                    brevvalgRevurdering = brevvalgRevurdering,
                )
            }
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: Utbetalinger,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        satsFactory: SatsFactory,
    ) = throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")

    sealed class KunneIkkeIverksetteRevurdering {
        data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()

        /**
         * Bør ikke oppstå ofte, siden saksbehandler ikke bør/kan påvirke avkortingen.
         * Her bør saksbehandler oppdatere revurderingen for å hente ny vedtaksdata med blant annet avkortingsdata fra saken.
         */
        data object Avkortingsfeil : KunneIkkeIverksetteRevurdering()
    }

    fun underkjenn(
        attestering: Attestering.Underkjent,
        oppgaveId: OppgaveId,
    ): UnderkjentRevurdering {
        return when (this) {
            is Innvilget -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

            is Opphørt -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )
        }
    }
}

private fun Revurdering.validerTilIverksettOvergang(
    attestant: NavIdentBruker.Attestant,
    uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
    saksbehandler: NavIdentBruker.Saksbehandler,
    avkorting: AvkortingVedRevurdering.Håndtert,
): Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering, Unit> {
    if (saksbehandler.navIdent == attestant.navIdent) {
        return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }

    when (avkorting) {
        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
            if (avkorting.avkortingsvarsel != uteståendeAvkortingPåSak) {
                log.error("Prøver annullere en avkorting som ikke er lik avkortingen på sak ${this.sakId} og revurdering ${this.id}")
                return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil.left()
            }
        }

        AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
            if (uteståendeAvkortingPåSak != null && this.periode.overlapper(uteståendeAvkortingPåSak.periode())) {
                log.error("Prøver å iverksette revurdering over periode som har utåestående avkorting. Må i så fall annuleres. For sak ${this.sakId} og revurdering ${this.id}")
                return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil.left()
            }
        }

        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
            log.error("Prøver å iverksette revurdering hvor vi ikke kan håndtere avkorting.")
            return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil.left()
        }

        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
            if (uteståendeAvkortingPåSak != null) {
                log.error("Prøver å iverksette revurdering med avkortingsvarsel når det allerede finnes utestående avkorting for sak ${this.sakId} og revurdering ${this.id}")
                return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil.left()
            }
        }

        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            if (avkorting.annullerUtestående != uteståendeAvkortingPåSak) {
                log.error("Prøver annullere en avkorting som ikke er lik avkortingen på sak ${this.sakId} og revurdering ${this.id}")
                return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil.left()
            }
        }
    }
    return Unit.right()
}
