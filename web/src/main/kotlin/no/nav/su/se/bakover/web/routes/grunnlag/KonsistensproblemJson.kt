package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.web.ErrorJson

fun Konsistensproblem.tilResultat() = when (this) {
    Konsistensproblem.Bosituasjon.Ufullstendig -> ErrorJson(
        message = "Bosituasjon er ufullstendig",
        code = "bosituasjone_er_ufullstendig",
    )
    Konsistensproblem.Bosituasjon.Mangler -> ErrorJson(
        message = "Bosituasjon mangler",
        code = "bosituasjon_mangler",
    )
    Konsistensproblem.Uføre.Mangler -> ErrorJson(
        message = "Uføregrunnlag mangler",
        code = "uføregrunnlag_mangler",
    )
    Konsistensproblem.BosituasjonOgFormue.PerioderForBosituasjonEPSOgFormueEPSSamsvarerIkke -> ErrorJson(
        message = "Ikke samsvar mellom bosituasjon og formue for EPS for alle perioder.",
        code = "ikke_samsvar_bosituasjon_formue_eps",
    )
    Konsistensproblem.BosituasjonOgFradrag.PerioderMedFradragUtenforPerioderMedBosituasjon -> ErrorJson(
        message = "Perioder for alle bosituasjoner overlapper ikke med perioder for alle fradrag",
        code = "ikke_overlapp_bosituasjon_fradrag",
    )
    Konsistensproblem.Bosituasjon.Overlapp -> ErrorJson(
        message = "Ikke lov med overlapp i perioder for bosituasjon. Hvar bosituasjon må ha en distinkt periode.",
        code = "bosituasjonsperioder_overlapper"
    )
    Konsistensproblem.BosituasjonOgFormue.PerioderForFormueErUtenforPerioderMedBostiuasjon -> ErrorJson(
        message = "Perioder for alle bosituasjoner overlapper ikke med perioder for alle fradrag",
        code = "ikke_overlapp_bosituasjon_formue"
    )
    is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> ErrorJson(
        message = "Ugyldig bosituasjon: ${this.feil}",
        code = "ugyldig_bosituasjon"
    )
    Konsistensproblem.BosituasjonOgFradrag.PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke -> ErrorJson(
        message = "Ikke samsvar mellom bosituasjon og fradrag for EPS for alle perioder.",
        code = "ikke_samsvar_bosituasjon_fradrag_eps",
    )
    is Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon -> ErrorJson(
        message = "Ugyldig bosituasjon: ${this.feil}",
        code = "ugyldig_bosituasjon"
    )
}
