package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.visitors.RevurderingVisitor
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed class SimulertRevurdering : Revurdering(), LeggTilVedtaksbrevvalg {

    abstract override val beregning: Beregning
    abstract override val simulering: Simulering
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun erÅpen() = true

    abstract override fun leggTilBrevvalg(brevvalgRevurdering: BrevvalgRevurdering.Valgt): SimulertRevurdering

    override fun lagForhåndsvarsel(
        person: Person,
        saksbehandlerNavn: String,
        fritekst: String,
        clock: Clock,
    ): Either<UgyldigTilstand, LagBrevRequest> {
        return tilbakekrevingsbehandling.skalTilbakekreve().fold(
            {
                LagBrevRequest.Forhåndsvarsel(
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = fritekst,
                    dagensDato = LocalDate.now(clock),
                    saksnummer = saksnummer,
                )
            },
            {
                LagBrevRequest.ForhåndsvarselTilbakekreving(
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = fritekst,
                    dagensDato = LocalDate.now(clock),
                    saksnummer = saksnummer,
                    bruttoTilbakekreving = simulering.hentFeilutbetalteBeløp().sum(),
                    tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
                )
            },
        ).right()
    }

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    abstract fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): SimulertRevurdering

    sealed interface KunneIkkeSendeInnvilgetRevurderingTilAttestering {
        object TilbakekrevingsbehandlingErIkkeFullstendig : KunneIkkeSendeInnvilgetRevurderingTilAttestering
        object BrevvalgMangler : KunneIkkeSendeInnvilgetRevurderingTilAttestering
    }

    override fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            clock = clock,
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

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
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
    ) : SimulertRevurdering() {
        override val erOpphørt = false

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): Innvilget {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

        override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<KunneIkkeSendeInnvilgetRevurderingTilAttestering, RevurderingTilAttestering.Innvilget> {
            val gyldigTilbakekrevingsbehandling = when (tilbakekrevingsbehandling) {
                is Tilbakekrev,
                is IkkeTilbakekrev,
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving,
                -> {
                    tilbakekrevingsbehandling
                }

                is IkkeAvgjort -> {
                    return KunneIkkeSendeInnvilgetRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig.left()
                }
            }

            if (brevvalgRevurdering !is BrevvalgRevurdering.Valgt) {
                return KunneIkkeSendeInnvilgetRevurderingTilAttestering.BrevvalgMangler.left()
            }

            return RevurderingTilAttestering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = attesteringsoppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting,
                tilbakekrevingsbehandling = gyldigTilbakekrevingsbehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            ).right()
        }

        override fun leggTilBrevvalg(
            brevvalgRevurdering: BrevvalgRevurdering.Valgt,
        ): Innvilget {
            return copy(
                brevvalgRevurdering = brevvalgRevurdering,
                saksbehandler = when (val bestemtAv = brevvalgRevurdering.bestemtAv) {
                    is BrevvalgRevurdering.BestemtAv.Behandler -> NavIdentBruker.Saksbehandler(bestemtAv.ident)
                    BrevvalgRevurdering.BestemtAv.Systembruker -> saksbehandler
                },
            )
        }
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
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
    ) : SimulertRevurdering(), LeggTilVedtaksbrevvalg {
        override val erOpphørt = true

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
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

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): Opphørt {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

        sealed interface KanIkkeSendeOpphørtRevurderingTilAttestering {
            object KanIkkeSendeEnOpphørtGReguleringTilAttestering : KanIkkeSendeOpphørtRevurderingTilAttestering
            object TilbakekrevingsbehandlingErIkkeFullstendig : KanIkkeSendeOpphørtRevurderingTilAttestering
            object BrevvalgMangler : KanIkkeSendeOpphørtRevurderingTilAttestering
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<KanIkkeSendeOpphørtRevurderingTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            }

            val gyldigTilbakekrevingsbehandling = when (tilbakekrevingsbehandling) {
                is Tilbakekrev,
                is IkkeTilbakekrev,
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving,
                -> {
                    tilbakekrevingsbehandling
                }

                is IkkeAvgjort -> {
                    return KanIkkeSendeOpphørtRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig.left()
                }
            }

            if (brevvalgRevurdering !is BrevvalgRevurdering.Valgt) {
                return KanIkkeSendeOpphørtRevurderingTilAttestering.BrevvalgMangler.left()
            }

            return RevurderingTilAttestering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = attesteringsoppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting,
                tilbakekrevingsbehandling = gyldigTilbakekrevingsbehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            ).right()
        }

        override fun leggTilBrevvalg(
            brevvalgRevurdering: BrevvalgRevurdering.Valgt,
        ): Opphørt {
            return copy(
                brevvalgRevurdering = brevvalgRevurdering,
                saksbehandler = when (val bestemtAv = brevvalgRevurdering.bestemtAv) {
                    is BrevvalgRevurdering.BestemtAv.Behandler -> NavIdentBruker.Saksbehandler(bestemtAv.ident)
                    BrevvalgRevurdering.BestemtAv.Systembruker -> saksbehandler
                },
            )
        }
    }
}
