package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.beregning.Sats.Companion.utledSats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson

internal data class BosituasjonJson(
    val type: String,
    val fnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?,
    val sats: String?,
    val periode: PeriodeJson,
)

internal fun List<Grunnlag.Bosituasjon>.toJson(): List<BosituasjonJson> {
    return this.map {
        it.toJson()
    }
}

internal fun Grunnlag.Bosituasjon.toJson(): BosituasjonJson {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen ->
            BosituasjonJson(
                type = "DELER_BOLIG_MED_VOKSNE",
                fnr = null,
                delerBolig = true,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = this.begrunnelse,
                sats = this.utledSats().toString(),
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning ->
            BosituasjonJson(
                type = "EPS_IKKE_UFØR_FLYKTNING",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = false,
                begrunnelse = this.begrunnelse,
                sats = this.utledSats().toString(),
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre ->
            BosituasjonJson(
                type = "EPS_OVER_67",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = this.begrunnelse,
                sats = this.utledSats().toString(),
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning ->
            BosituasjonJson(
                type = "EPS_UFØR_FLYKTNING",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = true,
                begrunnelse = this.begrunnelse,
                sats = this.utledSats().toString(),
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Fullstendig.Enslig ->
            BosituasjonJson(
                type = "ENSLIG",
                fnr = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = this.begrunnelse,
                sats = this.utledSats().toString(),
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Ufullstendig.HarEps ->
            BosituasjonJson(
                type = "UFULLSTENDIG_HAR_EPS",
                fnr = this.fnr.toString(),
                delerBolig = true,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null,
                sats = null,
                periode = this.periode.toJson(),
            )
        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps ->
            BosituasjonJson(
                type = "UFULLSTENDIG_HAR_IKKE_EPS",
                fnr = null,
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null,
                sats = null,
                periode = this.periode.toJson(),
            )
    }
}
