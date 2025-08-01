package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjonForRevurdering
import behandling.revurdering.domain.formue.KunneIkkeLeggeTilFormue
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.vilkår.opphold.KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock
import java.util.UUID

sealed interface BeregnetRevurdering : RevurderingKanBeregnes {
    abstract override val beregning: Beregning

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

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<Revurdering.KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(
        bosituasjon: List<Bosituasjon.Fullstendig>,
    ): Either<KunneIkkeLeggeTilBosituasjonForRevurdering, OpprettetRevurdering> {
        return oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)
    }

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false

    override fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        omgjøringsgrunn: Omgjøringsgrunn?,
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
            omgjøringsgrunn = omgjøringsgrunn,
        )
    }

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

    data class Innvilget(
        override val id: RevurderingId,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val attesteringer: Attesteringshistorikk,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
        override val omgjøringsgrunn: Omgjøringsgrunn?,
    ) : BeregnetRevurdering {

        override val erOpphørt = false

        override val simulering: Simulering? = null

        fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Uføregrunnlag>?) -> Either<SimuleringFeilet, Simulering>,
        ): Either<SimuleringFeilet, SimulertRevurdering.Innvilget> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrElse { throw IllegalStateException("Revurdering uføre: $id mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).mapLeft {
                it
            }.map {
                SimulertRevurdering.Innvilget(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    oppdatert = oppdatert,
                    tilRevurdering = tilRevurdering,
                    vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                    beregning = beregning,
                    simulering = it,
                    saksbehandler = saksbehandler,
                    oppgaveId = oppgaveId,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    sakinfo = sakinfo,
                    brevvalgRevurdering = brevvalgRevurdering,
                    omgjøringsgrunn = omgjøringsgrunn,
                )
            }
        }

        override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = emptyList()
    }

    data class Opphørt(
        override val id: RevurderingId,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val attesteringer: Attesteringshistorikk,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
        override val omgjøringsgrunn: Omgjøringsgrunn?,
    ) : BeregnetRevurdering {
        override val erOpphørt = true
        override val simulering: Simulering? = null

        fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            simuler: (opphørsperiode: Periode, saksbehandler: NavIdentBruker.Saksbehandler) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        ): Either<SimuleringFeilet, SimulertRevurdering.Opphørt> {
            val simulertUtbetaling = simuler(periode, saksbehandler)
                .getOrElse { return it.left() }
            return SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning,
                simulering = simulertUtbetaling.simulering,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
                omgjøringsgrunn = omgjøringsgrunn,
            ).right()
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

        fun opphørSkyldesVilkår(): Boolean {
            return VurderOpphørVedRevurdering.Vilkårsvurderinger(vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja
        }
    }
}
