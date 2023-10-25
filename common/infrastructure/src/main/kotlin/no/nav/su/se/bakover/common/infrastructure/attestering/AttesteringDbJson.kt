package no.nav.su.se.bakover.common.infrastructure.attestering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AttesteringDbJson.IverksattJson::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = AttesteringDbJson.UnderkjentJson::class, name = "Underkjent"),
)
sealed interface AttesteringDbJson {
    val attestant: String
    val opprettet: Tidspunkt

    fun toDomain(): Attestering

    data class IverksattJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
    ) : AttesteringDbJson {
        override fun toDomain(): Attestering.Iverksatt = Attestering.Iverksatt(
            attestant = NavIdentBruker.Attestant(attestant),
            opprettet = opprettet,
        )
    }

    data class UnderkjentJson(
        override val attestant: String,
        override val opprettet: Tidspunkt,
        val grunn: String,
        val kommentar: String,
    ) : AttesteringDbJson {
        override fun toDomain(): Attestering.Underkjent = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(attestant),
            opprettet = opprettet,
            grunn = when (grunn) {
                "INNGANGSVILKÅRENE_ER_FEILVURDERT" -> Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT
                "BEREGNINGEN_ER_FEIL" -> Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL
                "DOKUMENTASJON_MANGLER" -> Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER
                "VEDTAKSBREVET_ER_FEIL" -> Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL
                "ANDRE_FORHOLD" -> Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                else -> throw IllegalStateException("Ukjent grunn - Kunne ikke mappe $grunn til ${Attestering.Underkjent.Grunn::class.simpleName}")
            },
            kommentar = kommentar,
        )
    }

    companion object {
        fun Attestering.toDbJson(): AttesteringDbJson = when (this) {
            is Attestering.Iverksatt -> this.toDbJson()
            is Attestering.Underkjent -> this.toDbJson()
        }

        fun Attestering.Iverksatt.toDbJson(): IverksattJson = IverksattJson(
            attestant = attestant.navIdent,
            opprettet = opprettet,
        )

        fun Attestering.Underkjent.toDbJson(): UnderkjentJson = UnderkjentJson(
            attestant = attestant.navIdent,
            opprettet = opprettet,
            grunn = when (grunn) {
                Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT -> "INNGANGSVILKÅRENE_ER_FEILVURDERT"
                Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL -> "BEREGNINGEN_ER_FEIL"
                Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER -> "DOKUMENTASJON_MANGLER"
                Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL -> "VEDTAKSBREVET_ER_FEIL"
                Attestering.Underkjent.Grunn.ANDRE_FORHOLD -> "ANDRE_FORHOLD"
            },
            kommentar = kommentar,
        )
    }
}
