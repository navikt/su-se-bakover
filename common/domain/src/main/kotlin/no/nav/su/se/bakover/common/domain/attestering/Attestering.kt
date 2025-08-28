package no.nav.su.se.bakover.common.domain.attestering

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

data class Attesteringshistorikk private constructor(
    private val underlying: List<Attestering>,
) : List<Attestering> by underlying {

    init {
        require(this.filterIsInstance<Attestering.Iverksatt>().size <= 1) { "Kan bare ha 1 iverksatt innslag i listen. fant flere" }
    }

    companion object {
        fun empty(): Attesteringshistorikk {
            // Ønsker gjenbruke logikk + validering til create(...)
            return create(emptyList())
        }

        fun create(attestering: Attestering): Attesteringshistorikk = create(listOf(attestering))

        /**
         * For å gjenopprette en persistert [Attesteringshistorikk]
         */
        fun create(
            attesteringer: List<Attestering>,
        ): Attesteringshistorikk {
            // TODO jah: Denne vil feile for Klage dersom en oversendt klage kommer i retur vil vi "iverksette" to ganger. Selv om det ikke kan kalles en iverksetting.
            check(attesteringer.filterIsInstance<Attestering.Iverksatt>().size <= 1) {
                "Attesteringshistorikk kan maks inneholde en iverksetting, men var: $attesteringer"
            }
            return Attesteringshistorikk(
                attesteringer.sortedBy { it.opprettet.instant },
            )
        }
    }

    fun leggTilNyAttestering(attestering: Attestering): Attesteringshistorikk {
        check(this.all { it.opprettet.instant < attestering.opprettet.instant }) {
            "Kan ikke legge til en attestering som ikke er nyere enn den forrige attesteringen"
        }
        return create(attesteringer = this + attestering)
    }

    /** @throws NoSuchElementException hvis lista er tom */
    fun hentSisteAttestering(): Attestering = this.last()

    fun prøvHentSisteAttestering(): Attestering? = if (this.isEmpty()) null else this.last()

    fun hentSisteIverksatteAttesteringOrNull(): Attestering.Iverksatt? {
        return this.filterIsInstance<Attestering.Iverksatt>().singleOrNull()
    }

    fun erUnderkjent(): Boolean = this.any { it is Attestering.Underkjent }
}

sealed interface Attestering {
    val attestant: NavIdentBruker.Attestant
    val opprettet: Tidspunkt

    data class Iverksatt(
        override val attestant: NavIdentBruker.Attestant,
        override val opprettet: Tidspunkt,
    ) : Attestering

    data class Underkjent(
        override val attestant: NavIdentBruker.Attestant,
        override val opprettet: Tidspunkt,
        val grunn: UnderkjennAttesteringsgrunn,
        val kommentar: String,
    ) : Attestering

    data class retur(
        override val attestant: NavIdentBruker.Attestant,
        override val opprettet: Tidspunkt,
    ) : Attestering
}
