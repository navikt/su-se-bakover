package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.infrastructure.MånedJson
import no.nav.su.se.bakover.common.infrastructure.MånedJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.web.routes.grunnlag.fradrag.FradragskategoriJson
import no.nav.su.se.bakover.web.routes.grunnlag.fradrag.FradragskategoriJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.EksternDataJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.regulering.FradragsperiodeJson.Companion.toJson

internal data class EksternSupplementReguleringJson(
    val bruker: SupplementForJson? = null,
    val eps: List<SupplementForJson> = emptyList(),
) {
    companion object {
        fun EksternSupplementRegulering.toJson(): EksternSupplementReguleringJson {
            val søkersSupplement = this.bruker?.let { supplementForSøker ->
                SupplementForJson(
                    fnr = supplementForSøker.fnr.toString(),
                    fradragsperioder = supplementForSøker.perType.toJson(),
                    eksterneVedtaksdata = supplementForSøker.eksternedataForAlleTyper().toJson(),
                )
            }

            val epsSupplement = this.eps.map { supplementForEps ->
                SupplementForJson(
                    fnr = supplementForEps.fnr.toString(),
                    fradragsperioder = supplementForEps.perType.toJson(),
                    eksterneVedtaksdata = supplementForEps.eksternedataForAlleTyper().toJson(),
                )
            }

            return EksternSupplementReguleringJson(bruker = søkersSupplement, eps = epsSupplement)
        }
    }
}

internal data class FradragsperiodeJson(
    val fradragstype: FradragskategoriJson,
    val vedtaksperiodeEndring: VedtaksperiodeEndringJson,
    val vedtaksperiodeRegulering: List<VedtaksperiodeReguleringJson>,
) {
    companion object {
        fun List<ReguleringssupplementFor.PerType>.toJson(): List<FradragsperiodeJson> {
            return this.map {
                FradragsperiodeJson(
                    fradragstype = it.kategori.toJson(),
                    vedtaksperiodeEndring = it.endringsvedtak.let {
                        VedtaksperiodeEndringJson(it.måned.toJson(), it.beløp)
                    },
                    vedtaksperiodeRegulering = it.reguleringsvedtak.filterDuplicates().map {
                        VedtaksperiodeReguleringJson(it.periode.toJson(), it.beløp)
                    },
                )
            }
        }
    }
}

// TODO - test
fun List<Eksternvedtak.Regulering>.filterDuplicates(): List<Eksternvedtak.Regulering> {
    return this.groupBy { Pair(it.periode, it.beløp) }.map { it.value.first() }
}

internal data class SupplementForJson(
    val fnr: String,
    val fradragsperioder: List<FradragsperiodeJson>,
    val eksterneVedtaksdata: NonEmptyList<EksternDataJson>,
)

data class VedtaksperiodeEndringJson(
    val periode: MånedJson,
    val beløp: Int,
)

data class VedtaksperiodeReguleringJson(
    val periode: PeriodeMedOptionalTilOgMedJson,
    val beløp: Int,
)

data class EksternDataJson(
    val fnr: String,
    val sakstype: String,
    val vedtakstype: String,
    val fraOgMed: String,
    val tilOgMed: String?,
    val bruttoYtelse: String,
    val nettoYtelse: String,
    val ytelseskomponenttype: String,
    val bruttoYtelseskomponent: String,
    val nettoYtelseskomponent: String,
) {
    companion object {
        fun NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata>.toJson(): NonEmptyList<EksternDataJson> =
            this.map {
                EksternDataJson(
                    fnr = it.fnr,
                    sakstype = it.sakstype,
                    vedtakstype = it.vedtakstype,
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    bruttoYtelse = it.bruttoYtelse,
                    nettoYtelse = it.nettoYtelse,
                    ytelseskomponenttype = it.ytelseskomponenttype,
                    bruttoYtelseskomponent = it.bruttoYtelseskomponent,
                    nettoYtelseskomponent = it.nettoYtelseskomponent,
                )
            }
    }
}
