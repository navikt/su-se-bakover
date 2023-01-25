package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.VedtakSomRevurderesMånedsvis
import java.time.YearMonth
import java.util.UUID

internal data class VedtakSomRevurderesMånedsvisDbJson(
    val måneder: List<Måned>,
) {
    data class Måned(
        val måned: String,
        val vedtakId: String,
    )

    companion object {

        fun toDomain(json: String): VedtakSomRevurderesMånedsvis {
            val dbJson = deserialize<VedtakSomRevurderesMånedsvisDbJson>(json)
            return VedtakSomRevurderesMånedsvis(
                dbJson.måneder.map {
                    val måned = no.nav.su.se.bakover.common.periode.Måned.fra(YearMonth.parse(it.måned))
                    val vedtakId = UUID.fromString(it.vedtakId)
                    måned to vedtakId
                }.sortedBy { it.first }.toMap(),
            )
        }
    }
}

internal fun VedtakSomRevurderesMånedsvis.toDbJson(): String {
    return VedtakSomRevurderesMånedsvisDbJson(
        måneder = this.value.map {
            VedtakSomRevurderesMånedsvisDbJson.Måned(
                måned = it.key.toString(),
                vedtakId = it.value.toString(),
            )
        },
    ).let { serialize(it) }
}
