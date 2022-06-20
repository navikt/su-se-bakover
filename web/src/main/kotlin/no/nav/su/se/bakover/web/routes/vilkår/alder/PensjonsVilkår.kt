package no.nav.su.se.bakover.web.routes.vilkår.alder

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsopplysninger
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLagePensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePensjon
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.web.routes.Feilresponser
import java.util.UUID

internal data class PensjonsopplysningerJson(
    val folketrygd: PensjonsoppysningerSvarJson,
    val andreNorske: PensjonsoppysningerSvarJson,
    val utenlandske: PensjonsoppysningerSvarJson,
) {
    fun toDomain(): Pensjonsopplysninger {
        return Pensjonsopplysninger(
            folketrygd = Pensjonsopplysninger.Folketrygd(
                svar = folketrygd.toDomain(),
            ),
            andreNorske = Pensjonsopplysninger.AndreNorske(
                svar = andreNorske.toDomain(),
            ),
            utenlandske = Pensjonsopplysninger.Utenlandske(
                svar = utenlandske.toDomain(),
            ),
        )
    }
}

internal fun Pensjonsopplysninger.toJson(): PensjonsopplysningerJson {
    return PensjonsopplysningerJson(
        folketrygd = folketrygd.svar.toJson(),
        andreNorske = andreNorske.svar.toJson(),
        utenlandske = utenlandske.svar.toJson(),
    )
}

enum class PensjonsoppysningerSvarJson {
    JA,
    NEI,
    IKKE_AKTUELT;

    fun toDomain(): Pensjonsopplysninger.Svar {
        return when (this) {
            JA -> Pensjonsopplysninger.Svar.Ja
            NEI -> Pensjonsopplysninger.Svar.Nei
            IKKE_AKTUELT -> Pensjonsopplysninger.Svar.IkkeAktuelt
        }
    }
}

internal fun Pensjonsopplysninger.Svar.toJson(): PensjonsoppysningerSvarJson {
    return when (this) {
        Pensjonsopplysninger.Svar.IkkeAktuelt -> {
            PensjonsoppysningerSvarJson.IKKE_AKTUELT
        }
        Pensjonsopplysninger.Svar.Ja -> {
            PensjonsoppysningerSvarJson.JA
        }
        Pensjonsopplysninger.Svar.Nei -> {
            PensjonsoppysningerSvarJson.NEI
        }
    }
}

internal data class PensjonsVilkårJson(
    val vurderinger: List<VurderingsperiodePensjonsvilkårJson>,
    val resultat: String,
)

private fun Resultat.toJson(): String {
    return when (this) {
        Resultat.Avslag -> "VilkårIkkeOppfylt"
        Resultat.Innvilget -> "VilkårOppfylt"
        Resultat.Uavklart -> "Uavklart"
    }
}

internal data class VurderingsperiodePensjonsvilkårJson(
    val resultat: String,
    val periode: PeriodeJson,
    val pensjonsopplysninger: PensjonsopplysningerJson,
)

internal data class LeggTilVurderingsperiodePensjonsvilkårJson(
    val periode: PeriodeJson,
    val pensjonsopplysninger: PensjonsopplysningerJson,
) {
    fun toDomain(): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {
        val opprettet = Tidspunkt.now()
        return VurderingsperiodePensjon.tryCreate(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            vurderingsperiode = periode.toPeriode(),
            grunnlag = Pensjonsgrunnlag(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                periode = periode.toPeriode(),
                pensjonsopplysninger = pensjonsopplysninger.toDomain(),
            ),
        )
    }
}

internal fun List<LeggTilVurderingsperiodePensjonsvilkårJson>.toDomain(): Either<KunneIkkeLeggeTilPensjonsVilkår, PensjonsVilkår.Vurdert> {
    return map { it.toDomain() }.sequence()
        .mapLeft { KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår(it) }
        .flatMap { vurderingsperioder ->
            PensjonsVilkår.Vurdert.tryCreate(NonEmptyList.fromListUnsafe(vurderingsperioder))
                .mapLeft { KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår(it) }
        }
}

internal fun PensjonsVilkår.toJson(): PensjonsVilkårJson? {
    return when (this) {
        PensjonsVilkår.IkkeVurdert -> {
            null
        }
        is PensjonsVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun PensjonsVilkår.Vurdert.toJson(): PensjonsVilkårJson {
    return PensjonsVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = resultat.toJson(),
    )
}

internal fun VurderingsperiodePensjon.toJson(): VurderingsperiodePensjonsvilkårJson {
    return VurderingsperiodePensjonsvilkårJson(
        resultat = resultat.toJson(),
        periode = periode.toJson(),
        pensjonsopplysninger = grunnlag.pensjonsopplysninger.toJson(),
    )
}

internal fun KunneIkkeLeggeTilPensjonsVilkår.tilResultat(): no.nav.su.se.bakover.web.Resultat {
    return when (this) {
        is KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår -> {
            when (this.feil) {
                KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }
                KunneIkkeLagePensjonsVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                    Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                }
            }
        }
        is KunneIkkeLeggeTilPensjonsVilkår.Revurdering -> {
            when (val feil = this.feil) {
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                is Revurdering.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }
            }
        }
        is KunneIkkeLeggeTilPensjonsVilkår.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                is Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }
            }
        }
    }
}
