package no.nav.su.se.bakover.web.routes.grunnlag

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.Behandlingsfeilresponser

internal fun KunneIkkeLageGrunnlagsdata.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageGrunnlagsdata.FradragForEPSMenBosituasjonUtenEPS -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke legge til fradrag knyttet til EPS for en bruker som ikke har EPS.",
            "fradrag_for_eps_uten_eps",
        )

        KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon -> HttpStatusCode.BadRequest.errorJson(
            "Alle fradragsperiodene må være innenfor bosituasjonsperioden.",
            "fradragsperiode_utenfor_bosituasjonperiode",
        )

        KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag -> HttpStatusCode.BadRequest.errorJson(
            "Må ha et bosituasjon, før man legger til fradrag",
            "må_ha_bosituasjon_før_fradrag",
        )

        is KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag -> this.feil.tilResultat()
        is KunneIkkeLageGrunnlagsdata.Konsistenssjekk -> this.feil.tilResultat()
    }
}

internal fun Fradragsgrunnlag.UgyldigFradragsgrunnlag.tilResultat(): Resultat {
    return when (this) {
        Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> Behandlingsfeilresponser.ugyldigFradragstype
    }
}
