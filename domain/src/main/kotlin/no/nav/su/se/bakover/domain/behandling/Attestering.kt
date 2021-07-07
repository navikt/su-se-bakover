package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Attestering.Iverksatt::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = Attestering.Underkjent::class, name = "Underkjent"),
)
data class AttesteringHistorik(private val attesteringer: MutableList<Attestering>) {
    companion object {
        fun empty(): AttesteringHistorik {
            return AttesteringHistorik(mutableListOf())
        }
    }
    fun leggTilNyAttestering(attestering: Attestering): AttesteringHistorik {
        attesteringer.add(attestering)

        return this
    }

    fun hentSisteAttestering() = attesteringer.last()
    fun hentAttesteringer() = attesteringer.toList()
}

sealed class Attestering {
    abstract val attestant: NavIdentBruker.Attestant
    abstract val tidspunkt: Tidspunkt

    data class Iverksatt(override val attestant: NavIdentBruker.Attestant, override val tidspunkt: Tidspunkt) : Attestering()
    data class Underkjent(
        override val attestant: NavIdentBruker.Attestant,
        override val tidspunkt: Tidspunkt,
        val grunn: Grunn,
        val kommentar: String,
    ) : Attestering() {
        enum class Grunn {
            INNGANGSVILKÅRENE_ER_FEILVURDERT,
            BEREGNINGEN_ER_FEIL,
            DOKUMENTASJON_MANGLER,
            VEDTAKSBREVET_ER_FEIL,
            ANDRE_FORHOLD,
        }
    }
}
