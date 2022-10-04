package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt

data class Attesteringshistorikk private constructor(
    @JsonValue private val underlying: List<Attestering>,
) : List<Attestering> by underlying {

    companion object {
        fun empty(): Attesteringshistorikk {
            // Ønsker gjenbruke logikk + validering til create(...)
            return create(emptyList())
        }

        /**
         * For å gjenopprette en persistert [Attesteringshistorikk]
         */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun create(
            attesteringer: List<Attestering>,
        ): Attesteringshistorikk {
            // TODO jah: Denne vil feile for Klage dersom en oversendt klage kommer i retur vil vi "iverksette" to ganger. Selv om det ikke kan kalles en iverksetting.
            assert(attesteringer.filterIsInstance<Attestering.Iverksatt>().size <= 1) {
                "Attesteringshistorikk kan maks inneholde en iverksetting, men var: $attesteringer"
            }
            return Attesteringshistorikk(
                attesteringer.sortedBy { it.opprettet.instant },
            )
        }
    }

    fun leggTilNyAttestering(attestering: Attestering): Attesteringshistorikk {
        assert(this.all { it.opprettet.instant < attestering.opprettet.instant }) {
            "Kan ikke legge til en attestering som ikke er nyere enn den forrige attesteringen"
        }
        return create(attesteringer = this + attestering)
    }

    /** @throws NoSuchElementException hvis lista er tom */
    fun hentSisteAttestering(): Attestering = this.last()

    fun prøvHentSisteAttestering(): Attestering? = if (this.isEmpty()) null else this.last()

    /** @throws NoSuchElementException hvis lista er tom */
    fun sisteAttesteringErIverksatt(): Boolean = hentSisteAttestering() is Attestering.Iverksatt
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
