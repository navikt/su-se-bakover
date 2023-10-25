package no.nav.su.se.bakover.common.infrastructure.attestering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.domain.Attestering
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
            grunn = Attestering.Underkjent.Grunn.valueOf(grunn),
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
            grunn = grunn.toString(),
            kommentar = kommentar,
        )
    }
}
