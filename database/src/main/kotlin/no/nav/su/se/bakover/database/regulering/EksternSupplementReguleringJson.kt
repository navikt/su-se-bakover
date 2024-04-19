package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import java.time.LocalDate

internal data class EksternSupplementReguleringJson(
    val bruker: ReguleringssupplementForJson? = null,
    val eps: List<ReguleringssupplementForJson> = emptyList(),
) {
    fun toDomain(): EksternSupplementRegulering = EksternSupplementRegulering(
        bruker = null,
        eps = emptyList(),
    )

    // TODO - fiks når vi må lagre dem inn i basen
//    companion object {
//        fun EksternSupplementRegulering.toDbJson(): EksternSupplementReguleringJson = EksternSupplementReguleringJson(
//            bruker = null,
//            eps = emptyList(),
//        )
//    }
}

data class ReguleringssupplementForJson(
    val fnr: String,
    val perType: List<PerTypeJson>,
) {
    // TODO - fiks når vi må lagre dem inn i basen
//    fun toDomain(): ReguleringssupplementFor = ReguleringssupplementFor(
//        fnr = Fnr.tryCreate(fnr)!!,
//        perType = perType.map { it.toDomain() }.toNonEmptyList(),
//    )
//
//    companion object {
//        fun ReguleringssupplementFor.toDbJson(): ReguleringssupplementForJson = ReguleringssupplementForJson(
//            fnr = this.fnr.toString(),
//            perType = perType.map { it.toDbJson() },
//        )
//    }
}

data class PerTypeJson(
    val fradragsperiode: List<FradragsperiodeJson>,
    val type: String,
) {
    // TODO - fiks når vi må lagre dem inn i basen
//    fun toDomain(): ReguleringssupplementFor.PerType = ReguleringssupplementFor.PerType(
//        fradragsperioder = fradragsperiode.map { it.toDomain() }.toNonEmptyList(),
//        type = Fradragstype.from(Fradragstype.Kategori.valueOf(type), null),
//    )

//    companion object {
//        fun ReguleringssupplementFor.PerType.toDbJson(): PerTypeJson = PerTypeJson(
//            fradragsperiode = fradragsperioder.map { it.toDbJson() },
//            type = this.type.kategori.name,
//        )
//    }
}

data class FradragsperiodeJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val vedtakstype: String,
    val beløp: Int,
    val eksterndata: EksternData,
) {
    fun toDomain(): ReguleringssupplementFor.PerType.Fradragsperiode =
        ReguleringssupplementFor.PerType.Fradragsperiode(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            beløp = beløp,
            // TODO - DB type i neste PR
            vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.valueOf(vedtakstype),
            eksterndata = ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata(
                fnr = eksterndata.fnr,
                sakstype = eksterndata.sakstype,
                vedtakstype = eksterndata.vedtakstype,
                fraOgMed = eksterndata.fraOgMed,
                tilOgMed = eksterndata.tilOgMed,
                bruttoYtelse = eksterndata.bruttoYtelse,
                nettoYtelse = eksterndata.nettoYtelse,
                ytelseskomponenttype = eksterndata.ytelseskomponenttype,
                bruttoYtelseskomponent = eksterndata.bruttoYtelseskomponent,
                nettoYtelseskomponent = eksterndata.nettoYtelseskomponent,
            ),
        )

    data class EksternData(
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
    )

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.toDbJson(): FradragsperiodeJson = FradragsperiodeJson(
            fraOgMed = this.fraOgMed,
            tilOgMed = tilOgMed,
            beløp = beløp,
            vedtakstype = vedtakstype.toString(),
            eksterndata = EksternData(
                fnr = eksterndata.fnr,
                sakstype = eksterndata.sakstype,
                vedtakstype = eksterndata.vedtakstype,
                fraOgMed = eksterndata.fraOgMed,
                tilOgMed = eksterndata.tilOgMed,
                bruttoYtelse = eksterndata.bruttoYtelse,
                nettoYtelse = eksterndata.nettoYtelse,
                ytelseskomponenttype = eksterndata.ytelseskomponenttype,
                bruttoYtelseskomponent = eksterndata.bruttoYtelseskomponent,
                nettoYtelseskomponent = eksterndata.nettoYtelseskomponent,
            ),
        )
    }
}
