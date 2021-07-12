package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker

data class Attesteringshistorikk(private val attesteringer: MutableList<Attestering>) {
    init {
        attesteringer.sortBy {
            it.opprettet.instant
        }
    }

    companion object {
        fun empty(): Attesteringshistorikk {
            return Attesteringshistorikk(mutableListOf())
        }
    }
    fun leggTilNyAttestering(attestering: Attestering): Attesteringshistorikk {
        this.attesteringer.add(attestering)

        return this.copy()
    }

    fun hentSisteAttestering() = attesteringer.last()
    fun hentAttesteringer(): List<Attestering> = attesteringer.toList()
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Attestering.Iverksatt::class, name = "Iverksatt"),
    JsonSubTypes.Type(value = Attestering.Underkjent::class, name = "Underkjent"),
)
sealed class Attestering {
    abstract val attestant: NavIdentBruker.Attestant
    abstract val opprettet: Tidspunkt

    data class Iverksatt(override val attestant: NavIdentBruker.Attestant, override val opprettet: Tidspunkt) : Attestering()
    data class Underkjent(
        override val attestant: NavIdentBruker.Attestant,
        override val opprettet: Tidspunkt,
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
