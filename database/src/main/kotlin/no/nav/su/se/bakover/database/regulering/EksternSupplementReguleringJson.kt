package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.database.regulering.FradragsperiodeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.PerTypeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.ReguleringssupplementForJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal data class EksternSupplementReguleringJson(
    val bruker: ReguleringssupplementForJson?,
    val eps: List<ReguleringssupplementForJson>,
) {
    fun toDomain(): EksternSupplementRegulering = EksternSupplementRegulering(
        bruker = bruker?.toDomain(),
        eps = eps.map { it.toDomain() },
    )

    companion object {
        fun EksternSupplementRegulering.toDbJson(): EksternSupplementReguleringJson = EksternSupplementReguleringJson(
            bruker = bruker?.toDbJson(),
            eps = eps.map { it.toDbJson() },
        )
    }
}

data class ReguleringssupplementForJson(
    val fnr: String,
    val perType: List<PerTypeJson>,
) {
    fun toDomain(): ReguleringssupplementFor = ReguleringssupplementFor(
        fnr = Fnr.tryCreate(fnr)!!,
        perType = perType.map { it.toDomain() }.toNonEmptyList(),
    )

    companion object {
        fun ReguleringssupplementFor.toDbJson(): ReguleringssupplementForJson = ReguleringssupplementForJson(
            fnr = this.fnr.toString(),
            perType = perType.map { it.toDbJson() },
        )
    }
}

data class PerTypeJson(
    val fradragsperiode: List<FradragsperiodeJson>,
    val type: String,
) {
    fun toDomain(): ReguleringssupplementFor.PerType = ReguleringssupplementFor.PerType(
        fradragsperioder = fradragsperiode.map { it.toDomain() }.toNonEmptyList(),
        type = Fradragstype.from(Fradragstype.Kategori.valueOf(type), null),
    )

    companion object {
        fun ReguleringssupplementFor.PerType.toDbJson(): PerTypeJson = PerTypeJson(
            fradragsperiode = fradragsperioder.map { it.toDbJson() },
            type = this.type.kategori.name,
        )
    }
}

data class FradragsperiodeJson(
    val periode: PeriodeJson,
    val beløp: Int,
) {
    fun toDomain(): ReguleringssupplementFor.PerType.Fradragsperiode =
        ReguleringssupplementFor.PerType.Fradragsperiode(
            periode = periode.toPeriode(),
            beløp = beløp,
        )

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.toDbJson(): FradragsperiodeJson = FradragsperiodeJson(
            periode = periode.toJson(),
            beløp = beløp,
        )
    }
}
