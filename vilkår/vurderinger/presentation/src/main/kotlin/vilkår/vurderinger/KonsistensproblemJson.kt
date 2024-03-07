package vilkår.vurderinger

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import vilkår.vurderinger.domain.Konsistensproblem

fun Konsistensproblem.tilResultat(): Resultat {
    return when (this) {
        is Konsistensproblem.Bosituasjon -> this.tilResultat()
        is Konsistensproblem.BosituasjonOgFormue -> this.tilResultat()
        is Konsistensproblem.BosituasjonOgFradrag -> this.tilResultat()
        is Konsistensproblem.Formue -> this.tilResultat()
        is Konsistensproblem.Uføre -> this.tilResultat()
    }
}

fun Konsistensproblem.BosituasjonOgFormue.tilResultat(): Resultat {
    return when (this) {
        Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig -> ErrorJson(
            message = "Ugyldig kombinasjon av bosituasjon og formue for hele eller deler av perioden",
            code = "ugyldig_kombinasjon_bosituasjon_formue",
        )

        Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode -> ErrorJson(
            message = "Formue mangler for periode med bosituasjon",
            code = "ingen_formue_for_bosituasjonsperiode",
        )

        is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> ErrorJson(
            message = "Ugyldig bosituasjon: ${this.feil}",
            code = "ugyldig_bosituasjon",
        )

        Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode -> ErrorJson(
            message = "Formue for EPS mangler for en eller flere av periodene.",
            code = "ingen_formue_eps_for_bosituasjonsperiode",
        )

        is Konsistensproblem.BosituasjonOgFormue.UgyldigFormue -> ErrorJson(
            message = "Ugyldig formue: ${this.feil}",
            code = "ugyldig_formue",
        )
    }.let {
        HttpStatusCode.BadRequest.errorJson(
            message = it.message,
            code = it.code,
        )
    }
}

internal fun Konsistensproblem.Bosituasjon.tilResultat(): Resultat {
    return when (this) {
        Konsistensproblem.Bosituasjon.Ufullstendig -> ErrorJson(
            message = "Bosituasjon er ufullstendig",
            code = "bosituasjone_er_ufullstendig",
        )

        Konsistensproblem.Bosituasjon.Mangler -> ErrorJson(
            message = "Bosituasjon mangler",
            code = "bosituasjon_mangler",
        )

        Konsistensproblem.Bosituasjon.Overlapp -> ErrorJson(
            message = "Ikke lov med overlapp i perioder for bosituasjon. Hvar bosituasjon må ha en distinkt periode.",
            code = "bosituasjonsperioder_overlapper",
        )
    }.let {
        HttpStatusCode.BadRequest.errorJson(
            message = it.message,
            code = it.code,
        )
    }
}

fun Konsistensproblem.BosituasjonOgFradrag.tilResultat(): Resultat {
    return when (this) {
        Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode -> ErrorJson(
            message = "Bosituasjon mangler for periode med fradrag.",
            code = "ingen_bosituasjon_for_fradragsperiode",
        )

        Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig -> ErrorJson(
            message = "Ugyldig kombinasjon av bosituasjon og fradrag for hele eller deler av perioden.",
            code = "ugyldig_kombinasjon_bosituasjon_fradrag",
        )

        is Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon -> ErrorJson(
            message = "Ugyldig bosituasjon: ${this.feil}",
            code = "ugyldig_bosituasjon",
        )
    }.let {
        HttpStatusCode.BadRequest.errorJson(
            message = it.message,
            code = it.code,
        )
    }
}

fun Konsistensproblem.Formue.tilResultat(): Resultat {
    return when (this) {
        Konsistensproblem.Formue.Mangler -> ErrorJson(
            message = "Formue mangler",
            code = "formue_mangler",
        )

        Konsistensproblem.Formue.Overlapp -> ErrorJson(
            message = "Perioder med formue overlapper.",
            code = "formue_overlapper",
        )
    }.let {
        HttpStatusCode.BadRequest.errorJson(
            message = it.message,
            code = it.code,
        )
    }
}

fun Konsistensproblem.Uføre.tilResultat(): Resultat {
    return when (this) {
        Konsistensproblem.Uføre.Mangler -> ErrorJson(
            message = "Uføregrunnlag mangler",
            code = "uføregrunnlag_mangler",
        )
    }.let {
        HttpStatusCode.BadRequest.errorJson(
            message = it.message,
            code = it.code,
        )
    }
}
