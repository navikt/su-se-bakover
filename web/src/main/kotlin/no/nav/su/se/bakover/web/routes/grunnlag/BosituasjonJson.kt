package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon

internal data class BosituasjonJson(
    val type: String,
    val fnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val sats: String?,
    val periode: PeriodeJson,
)

internal fun List<Bosituasjon>.toJson(): List<BosituasjonJson> {
    return this.map {
        it.toJson()
    }
}

internal fun Bosituasjon.toJson(): BosituasjonJson {
    return when (this) {
        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen ->
            BosituasjonJson(
                type = "DELER_BOLIG_MED_VOKSNE",
                fnr = null,
                delerBolig = true,
                ektemakeEllerSamboerUførFlyktning = null,
                sats = this.satskategori.toString(),
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning ->
            BosituasjonJson(
                type = "EPS_IKKE_UFØR_FLYKTNING",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = false,
                sats = this.satskategori.toString(),
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre ->
            BosituasjonJson(
                type = "EPS_OVER_67",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = null,
                sats = this.satskategori.toString(),
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning ->
            BosituasjonJson(
                type = "EPS_UFØR_FLYKTNING",
                fnr = this.fnr.toString(),
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = true,
                sats = this.satskategori.toString(),
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Fullstendig.Enslig ->
            BosituasjonJson(
                type = "ENSLIG",
                fnr = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = null,
                sats = this.satskategori.toString(),
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Ufullstendig.HarEps ->
            BosituasjonJson(
                type = "UFULLSTENDIG_HAR_EPS",
                fnr = this.fnr.toString(),
                delerBolig = true,
                ektemakeEllerSamboerUførFlyktning = null,
                sats = null,
                periode = this.periode.toJson(),
            )
        is Bosituasjon.Ufullstendig.HarIkkeEps ->
            BosituasjonJson(
                type = "UFULLSTENDIG_HAR_IKKE_EPS",
                fnr = null,
                delerBolig = null,
                ektemakeEllerSamboerUførFlyktning = null,
                sats = null,
                periode = this.periode.toJson(),
            )
    }
}
