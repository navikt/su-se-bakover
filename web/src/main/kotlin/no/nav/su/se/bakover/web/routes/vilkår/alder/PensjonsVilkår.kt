package no.nav.su.se.bakover.web.routes.vilkår.alder

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.sequence
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsopplysninger
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLagePensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePensjon
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.routes.Feilresponser
import java.util.UUID

internal data class PensjonsopplysningerJson(
    val folketrygd: PensjonsoppysningerSvarJson,
    val andreNorske: PensjonsoppysningerSvarJson,
    val utenlandske: PensjonsoppysningerSvarJson,
) {
    fun toDomain(): Pensjonsopplysninger {
        return Pensjonsopplysninger(
            søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(
                svar = folketrygd.toFolketrygdSvar(),
            ),
            søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(
                svar = andreNorske.toAndrePensjonerSvar(),
            ),
            søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(
                svar = utenlandske.toUtenlandskePensjonerSvar(),
            ),
        )
    }
}

internal fun Pensjonsopplysninger.toJson(): PensjonsopplysningerJson {
    return PensjonsopplysningerJson(
        folketrygd = søktPensjonFolketrygd.svar.toJson(),
        andreNorske = søktAndreNorskePensjoner.svar.toJson(),
        utenlandske = søktUtenlandskePensjoner.svar.toJson(),
    )
}

private fun Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.toJson(): PensjonsoppysningerSvarJson {
    return when (this) {
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarIkkeSøktUtenlandskePensjoner -> {
            PensjonsoppysningerSvarJson.NEI
        }
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner -> {
            PensjonsoppysningerSvarJson.JA
        }
        Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.IkkeAktuelt -> {
            PensjonsoppysningerSvarJson.IKKE_AKTUELT
        }
    }
}

private fun Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.toJson(): PensjonsoppysningerSvarJson {
    return when (this) {
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarIkkeSøktAndreNorskePensjonerEnnFolketrygden -> {
            PensjonsoppysningerSvarJson.NEI
        }
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarSøktAndreNorskePensjonerEnnFolketrygden -> {
            PensjonsoppysningerSvarJson.JA
        }
        Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt -> {
            PensjonsoppysningerSvarJson.IKKE_AKTUELT
        }
    }
}

private fun Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.toJson(): PensjonsoppysningerSvarJson {
    return when (this) {
        Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden -> {
            PensjonsoppysningerSvarJson.NEI
        }
        Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden -> {
            PensjonsoppysningerSvarJson.JA
        }
    }
}

enum class PensjonsoppysningerSvarJson {
    JA,
    NEI,
    IKKE_AKTUELT,
    ;

    fun toFolketrygdSvar(): Pensjonsopplysninger.SøktPensjonFolketrygd.Svar {
        return when (this) {
            JA -> {
                Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden
            }
            NEI -> {
                Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden
            }
            IKKE_AKTUELT -> {
                throw IllegalArgumentException("Ugyldig argument $this for ${Pensjonsopplysninger.SøktPensjonFolketrygd.Svar::class}")
            }
        }
    }

    fun toAndrePensjonerSvar(): Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar {
        return when (this) {
            JA -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarSøktAndreNorskePensjonerEnnFolketrygden
            }
            NEI -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.HarIkkeSøktAndreNorskePensjonerEnnFolketrygden
            }
            IKKE_AKTUELT -> {
                Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt
            }
        }
    }

    fun toUtenlandskePensjonerSvar(): Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar {
        return when (this) {
            JA -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner
            }
            NEI -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarIkkeSøktUtenlandskePensjoner
            }
            IKKE_AKTUELT -> {
                Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.IkkeAktuelt
            }
        }
    }
}

internal data class PensjonsVilkårJson(
    val vurderinger: List<VurderingsperiodePensjonsvilkårJson>,
    val resultat: String,
)

private fun Vurdering.toJson(): String {
    return when (this) {
        Vurdering.Avslag -> "VilkårIkkeOppfylt"
        Vurdering.Innvilget -> "VilkårOppfylt"
        Vurdering.Uavklart -> "Uavklart"
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
        val opprettet = fixedTidspunkt
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
        resultat = vurdering.toJson(),
    )
}

internal fun VurderingsperiodePensjon.toJson(): VurderingsperiodePensjonsvilkårJson {
    return VurderingsperiodePensjonsvilkårJson(
        resultat = vurdering.toJson(),
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
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }
            }
        }
    }
}
