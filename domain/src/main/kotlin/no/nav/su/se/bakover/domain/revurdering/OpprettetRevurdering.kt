package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.formue.KunneIkkeLeggeTilFormue
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
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
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

data class OpprettetRevurdering(
    override val id: RevurderingId = RevurderingId.generer(),
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val oppdatert: Tidspunkt,
    override val tilRevurdering: UUID,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val oppgaveId: OppgaveId,
    override val revurderingsårsak: Revurderingsårsak,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
    override val informasjonSomRevurderes: InformasjonSomRevurderes,
    override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    override val sakinfo: SakInfo,
    override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
) : RevurderingKanBeregnes {
    override val erOpphørt = false
    override val beregning: Beregning? = null
    override val simulering: Simulering? = null

    override fun erÅpen() = true

    override fun skalTilbakekreve() = false

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(
        formue: FormueVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(
        vilkår: PensjonsVilkår.Vurdert,
    ): Either<Revurdering.KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(
        vilkår: FlyktningVilkår.Vurdert,
    ): Either<Revurdering.KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(
        vilkår: PersonligOppmøteVilkår.Vurdert,
    ): Either<Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<Revurdering.KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(
        institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    fun oppdaterInformasjonSomRevurderes(informasjonSomRevurderes: InformasjonSomRevurderes): OpprettetRevurdering {
        return copy(informasjonSomRevurderes = informasjonSomRevurderes)
    }

    /**
     * Ikke ment å bli kalt som saksbehandler.
     * Bruk heller [oppdaterFradragOgMarkerSomVurdert]
     */
    override fun oppdaterFradrag(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<Revurdering.KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun skalSendeVedtaksbrev() = brevvalgRevurdering.skalSendeBrev().isRight()

    override fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
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

    override fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = emptyList()
}
