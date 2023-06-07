package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

data class Behandlinger(
    val søknadsbehandlinger: List<Søknadsbehandling>,
    val revurderinger: List<AbstraktRevurdering>,
    val reguleringer: List<Regulering>,
    val klager: List<Klage>,
) {

    companion object {
        fun empty() = Behandlinger(emptyList(), emptyList(), emptyList(), emptyList())
    }

    fun nySøknadsbehandling(søknadsbehandling: Søknadsbehandling): Behandlinger {
        require(søknadsbehandlinger.none { it.id == søknadsbehandling.id }) {
            "Søknadsbehandling med id ${søknadsbehandling.id} finnes fra før."
        }
        return this.copy(søknadsbehandlinger = this.søknadsbehandlinger + søknadsbehandling)
    }

    fun oppdaterSøknadsbehandling(søknadsbehandling: Søknadsbehandling): Behandlinger {
        require(søknadsbehandlinger.any { it.id == søknadsbehandling.id }) {
            "Søknadsbehandling med id ${søknadsbehandling.id} finnes ikke fra før."
        }
        return this.copy(
            søknadsbehandlinger = this.søknadsbehandlinger.map {
                if (it.id == søknadsbehandling.id) søknadsbehandling else it
            },
        )
    }

    fun nyRevurdering(revurdering: AbstraktRevurdering): Behandlinger {
        require(revurderinger.none { it.id == revurdering.id }) {
            "Revurdering med id ${revurdering.id} finnes fra før."
        }
        return this.copy(revurderinger = this.revurderinger + revurdering)
    }

    fun oppdaterRevurdering(revurdering: AbstraktRevurdering): Behandlinger {
        require(revurderinger.any { it.id == revurdering.id }) {
            "Revurdering med id ${revurdering.id} finnes ikke fra før."
        }
        return this.copy(
            revurderinger = this.revurderinger.map {
                if (it.id == revurdering.id) revurdering else it
            },
        )
    }

    fun nyRegulering(regulering: Regulering): Behandlinger {
        require(reguleringer.none { it.id == regulering.id }) {
            "Regulering med id ${regulering.id} finnes fra før."
        }
        return this.copy(reguleringer = this.reguleringer + regulering)
    }

    fun oppdaterRegulering(regulering: Regulering): Behandlinger {
        require(reguleringer.any { it.id == regulering.id }) {
            "Regulering med id ${regulering.id} finnes ikke fra før."
        }
        return this.copy(
            reguleringer = this.reguleringer.map {
                if (it.id == regulering.id) regulering else it
            },
        )
    }

    fun nyKlage(klage: Klage): Behandlinger {
        require(klager.none { it.id == klage.id }) {
            "Klage med id ${klage.id} finnes fra før."
        }
        return this.copy(klager = this.klager + klage)
    }

    fun oppdaterKlage(klage: Klage): Behandlinger {
        require(klager.any { it.id == klage.id }) {
            "Klage med id ${klage.id} finnes ikke fra før."
        }
        return this.copy(
            klager = this.klager.map {
                if (it.id == klage.id) klage else it
            },
        )
    }

    init {
        requireDistinctIds()
        requireSameSakId()
        requireDistinctOpprettet()
        requireSortedByOpprettet()
    }

    private fun requireSortedByOpprettet() {
        listOf(
            søknadsbehandlinger.map { it.opprettet },
            revurderinger.map { it.opprettet },
            reguleringer.map { it.opprettet },
            klager.map { it.opprettet },
        ).forEach {
            require(it.sortedBy { it.instant } == it) {
                "Behandlinger (søknadsbehandlinger, revurderinger, reguleringer og klager) er ikke sortert i stigende rekkefølge: $it"
            }
        }
    }

    private fun requireDistinctIds() {
        listOf(
            søknadsbehandlinger.map { it.id },
            revurderinger.map { it.id },
            reguleringer.map { it.id },
            klager.map { it.id },
        ).flatten().let {
            require(it.distinct().size == it.size) {
                "Behandlinger (søknadsbehandlinger, revurderinger, reguleringer og klager) inneholder duplikate id-er: $it"
            }
        }
    }

    private fun requireSameSakId() {
        listOf(
            søknadsbehandlinger.map { it.sakId },
            revurderinger.map { it.sakId },
            reguleringer.map { it.sakId },
            klager.map { it.sakId },
        ).flatten().distinct().let {
            require(it.size <= 1) {
                "Behandlinger (søknadsbehandlinger, revurderinger, reguleringer og klager) inneholder ulike sakId-er: $it"
            }
        }
    }

    private fun requireDistinctOpprettet() {
        listOf(
            søknadsbehandlinger.map { it.opprettet },
            revurderinger.map { it.opprettet },
            reguleringer.map { it.opprettet },
            klager.map { it.opprettet },
        ).forEach {
            require(it.distinct().size == it.size) {
                "Behandlinger (søknadsbehandlinger, revurderinger, reguleringer og klager) inneholder duplikate opprettet-tidspunkt: $it"
            }
        }
    }
}
