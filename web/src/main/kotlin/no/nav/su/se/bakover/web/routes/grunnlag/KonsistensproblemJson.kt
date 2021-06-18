package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.web.ErrorJson

fun Konsistensproblem.tilResultat() = when (this) {
    Konsistensproblem.Bosituasjon.Flere -> ErrorJson(
        message = "Flere bosituasjoner støttes ikke",
        code = "flere_bosituasjoner_støttes_ikke",
    )
    Konsistensproblem.Bosituasjon.Ufullstendig -> ErrorJson(
        message = "Bosituasjon er ufullstendig",
        code = "bosituasjone_er_ufullstendig",
    )
    Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS -> ErrorJson(
        message = "Flere bosituasjoner og fradrag for EPS",
        code = "flere_bosituasjoner_og_fradrag_for_eps",
    )
    Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS -> ErrorJson(
        message = "Har fradrag for EPS, men ingen EPS er registrert.",
        code = "fradrag_for_eps_ingen_eps_registrert",
    )
    Konsistensproblem.Bosituasjon.Mangler -> ErrorJson(
        message = "Bosituasjon mangler",
        code = "bosituasjon_mangler",
    )
    Konsistensproblem.Uføre.Mangler -> ErrorJson(
        message = "Uføregrunnlag mangler",
        code = "uføregrunnlag_mangler",
    )
    Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS -> ErrorJson(
        message = "Flere bosituasjoner og formue for EPS",
        code = "flere_bosituasjoner_og_formue_for_eps",
    )
    Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS -> ErrorJson(
        message = "Har formue for EPS, men ingen EPS er registrert.",
        code = "formue_for_eps_ingen_eps_registrert",
    )
}
