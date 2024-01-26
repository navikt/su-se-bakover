package no.nav.su.se.bakover.web.routes.vilkår.pensjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.tilResultat
import vilkår.common.domain.Vurdering
import vilkår.pensjon.domain.KunneIkkeLagePensjonsVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.pensjon.domain.Pensjonsgrunnlag
import vilkår.pensjon.domain.Pensjonsopplysninger
import vilkår.pensjon.domain.VurderingsperiodePensjon
import java.time.Clock
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
    fun toDomain(clock: Clock): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {
        val opprettet = Tidspunkt.now(clock)
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

internal fun List<LeggTilVurderingsperiodePensjonsvilkårJson>.toDomain(clock: Clock): Either<KunneIkkeLeggeTilPensjonsVilkår, PensjonsVilkår.Vurdert> {
    return map {
        it.toDomain(clock).getOrElse { return KunneIkkeLeggeTilPensjonsVilkår.UgyldigPensjonsVilkår(it).left() }
    }.let { vurderingsperioder ->
        PensjonsVilkår.Vurdert.tryCreate(vurderingsperioder.toNonEmptyList())
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

internal fun KunneIkkeLeggeTilPensjonsVilkår.tilResultat(): Resultat {
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
            when (val f = this.feil) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder -> {
                    Feilresponser.vilkårKunRelevantForAlder
                }

                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.Vilkårsfeil -> f.underliggende.tilResultat()
            }
        }
    }
}
