package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import beregning.domain.Beregning
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselTilbakekrevingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.vilkår.opphold.KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.uføre.domain.UføreVilkår
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

sealed class UnderkjentRevurdering : RevurderingKanBeregnes(), LeggTilVedtaksbrevvalg {
    abstract override val beregning: Beregning
    abstract override val attesteringer: Attesteringshistorikk
    val attestering: Attestering.Underkjent
        get() = attesteringer.hentSisteAttestering() as Attestering.Underkjent
    abstract override val brevvalgRevurdering: BrevvalgRevurdering.Valgt

    abstract val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling

    override fun erÅpen() = true

    abstract override fun leggTilBrevvalg(brevvalgRevurdering: BrevvalgRevurdering.Valgt): UnderkjentRevurdering

    override fun skalTilbakekreve() = tilbakekrevingsbehandling.skalTilbakekreve().isRight()

    abstract fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling): UnderkjentRevurdering

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Fradragsgrunnlag>) =
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

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            clock = clock,
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
        )
    }

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
        override val attesteringer: Attesteringshistorikk,
        override val simulering: Simulering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : UnderkjentRevurdering() {
        override val erOpphørt = false

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling): Innvilget {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ) = RevurderingTilAttestering.Innvilget(
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
            attesteringer = attesteringer,
            tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        )

        override fun lagForhåndsvarsel(
            utførtAv: NavIdentBruker.Saksbehandler,
            fritekst: String,
        ): Either<UgyldigTilstand, GenererDokumentCommand> {
            return tilbakekrevingsbehandling.skalTilbakekreve().fold(
                {
                    ForhåndsvarselDokumentCommand(
                        fødselsnummer = fnr,
                        saksnummer = saksnummer,
                        saksbehandler = utførtAv,
                        fritekst = fritekst,

                    )
                },
                {
                    ForhåndsvarselTilbakekrevingDokumentCommand(
                        fødselsnummer = fnr,
                        saksnummer = saksnummer,
                        saksbehandler = utførtAv,
                        fritekst = fritekst,
                        bruttoTilbakekreving = simulering.hentFeilutbetalteBeløp().sum(),
                        tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
                    )
                },
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
        override val tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering.Valgt,
    ) : UnderkjentRevurdering() {
        override val erOpphørt = true

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling): Opphørt {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

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

        override fun lagForhåndsvarsel(
            utførtAv: NavIdentBruker.Saksbehandler,
            fritekst: String,
        ): Either<UgyldigTilstand, GenererDokumentCommand> {
            return tilbakekrevingsbehandling.skalTilbakekreve().fold(
                {
                    ForhåndsvarselDokumentCommand(
                        fødselsnummer = fnr,
                        saksnummer = saksnummer,
                        saksbehandler = utførtAv,
                        fritekst = fritekst,
                    )
                },
                {
                    ForhåndsvarselTilbakekrevingDokumentCommand(
                        fødselsnummer = fnr,
                        saksnummer = saksnummer,
                        saksbehandler = utførtAv,
                        fritekst = fritekst,
                        bruttoTilbakekreving = simulering.hentFeilutbetalteBeløp().sum(),
                        tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
                    )
                },
            ).right()
        }

        data object KanIkkeSendeEnOpphørtGReguleringTilAttestering

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<KanIkkeSendeEnOpphørtGReguleringTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            } else {
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
                    oppgaveId = oppgaveId,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                    sakinfo = sakinfo,
                    brevvalgRevurdering = brevvalgRevurdering,
                ).right()
            }
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
