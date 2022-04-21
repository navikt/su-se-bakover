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
    Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig -> ErrorJson(
        message = "Ugyldig kombinasjon av bosituasjon og formue for hele eller deler av perioden",
        code = "ugyldig_kombinasjon_bosituasjon_formue",
    )
    Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode -> ErrorJson(
        message = "Bosituasjon mangler for periode med fradrag.",
        code = "ingen_bosituasjon_for_fradragsperiode",
    )
    Konsistensproblem.Bosituasjon.Overlapp -> ErrorJson(
        message = "Ikke lov med overlapp i perioder for bosituasjon. Hvar bosituasjon må ha en distinkt periode.",
        code = "bosituasjonsperioder_overlapper",
    )
    Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode -> ErrorJson(
        message = "Formue mangler for periode med bosituasjon",
        code = "ingen_formue_for_bosituasjonsperiode",
    )
    is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> ErrorJson(
        message = "Ugyldig bosituasjon: ${this.feil}",
        code = "ugyldig_bosituasjon",
    )
    Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig -> ErrorJson(
        message = "Ugyldig kombinasjon av bosituasjon og fradrag for hele eller deler av perioden.",
        code = "ugyldig_kombinasjon_bosituasjon_fradrag",
    )
    is Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon -> ErrorJson(
        message = "Ugyldig bosituasjon: ${this.feil}",
        code = "ugyldig_bosituasjon",
    )
    Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode -> ErrorJson(
        message = "Formue for EPS mangler for en eller flere av periodene.",
        code = "ingen_formue_eps_for_bosituasjonsperiode"
    )
}
