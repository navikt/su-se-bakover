package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.NavIdentBruker

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Attestering.Iverksatt::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = Attestering.Underkjent::class, name = "Underkjent"),
)
sealed class Attestering {
    abstract val attestant: NavIdentBruker.Attestant

    data class Iverksatt(override val attestant: NavIdentBruker.Attestant) : Attestering()
    data class Underkjent(
        override val attestant: NavIdentBruker.Attestant,
        val underkjennelse: Underkjennelse,
    ) : Attestering() {
        data class Underkjennelse(
            val grunn: Grunn,
            val kommentar: String
        ) {
            enum class Grunn {
                INNGANGSVILKÅRENE_ER_FEILVURDERT,
                BEREGNINGEN_ER_FEIL,
                DOKUMENTASJON_MANGLER,
                VEDTAKSBREVET_ER_FEIL,
                ANDRE_FORHOLD,
            }
        }
    }
}
