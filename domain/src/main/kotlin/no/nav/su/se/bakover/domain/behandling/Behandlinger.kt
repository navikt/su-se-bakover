package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandlinger
import java.util.UUID

data class Behandlinger(
    val søknadsbehandlinger: List<Søknadsbehandling>,
    val revurderinger: List<AbstraktRevurdering>,
    val reguleringer: Reguleringer,
    val klager: List<Klage>,
    val tilbakekrevinger: Tilbakekrevingsbehandlinger,
) {
    val saksnummer: Saksnummer? = søknadsbehandlinger.firstOrNull()?.saksnummer
    val fnr: Fnr? = søknadsbehandlinger.firstOrNull()?.fnr
    val sakstype: Sakstype? = søknadsbehandlinger.firstOrNull()?.sakstype
    val sakId: UUID? = søknadsbehandlinger.firstOrNull()?.sakId

    companion object {
        fun empty(sakId: UUID) = Behandlinger(
            søknadsbehandlinger = emptyList(),
            revurderinger = emptyList(),
            reguleringer = Reguleringer.empty(sakId = sakId),
            klager = emptyList(),
            tilbakekrevinger = Tilbakekrevingsbehandlinger.empty(sakId = sakId),
        )

        fun List<Søknadsbehandling>.harBehandlingUnderArbeid(): Boolean = this.any { it.erÅpen() }
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

    /**
     * @throws IllegalStateException hvis regulering med samme id finnes fra før.
     */
    fun nyRegulering(regulering: Regulering): Behandlinger {
        return this.copy(reguleringer = reguleringer.nyRegulering(regulering))
    }

    /**
     * @throws IllegalStateException hvis regulering med samme id ikke finnes fra før.
     */
    fun oppdaterRegulering(regulering: Regulering): Behandlinger {
        return this.copy(reguleringer = reguleringer.oppdaterRegulering(regulering))
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
        requireDistinctOpprettet()
        requireSortedByOpprettet()
        requireRekkefølgePåBehandlinger()
        requireSameSakId()
        requireSameSaksnummer()
        requireSameFnr()
        requireSameSakstype()
    }

    private fun requireSameSakstype() {
        listOf(
            søknadsbehandlinger.map { it.sakstype },
            revurderinger.map { it.sakstype },
            reguleringer.map { it.sakstype },
        ).flatten().let {
            require(it.distinct().size <= 1) {
                "Alle behandlinger må ha samme sakstype: $it"
            }
        }
    }

    private fun requireSameFnr() {
        listOf(
            søknadsbehandlinger.map { it.fnr },
            revurderinger.map { it.fnr },
            reguleringer.map { it.fnr },
            klager.map { it.fnr },
        ).flatten().let {
            require(it.distinct().size <= 1) {
                "Alle behandlinger på en sak ($saksnummer) må ha samme fnr: $it"
            }
        }
    }

    private fun requireSameSaksnummer() {
        listOf(
            søknadsbehandlinger.map { it.saksnummer },
            revurderinger.map { it.saksnummer },
            reguleringer.map { it.saksnummer },
            klager.map { it.saksnummer },
        ).flatten().let {
            require(it.distinct().size <= 1) {
                "Alle behandlinger  må ha samme saksnummer: $it"
            }
        }
    }

    private fun requireRekkefølgePåBehandlinger() {
        if (revurderinger.isNotEmpty() || reguleringer.isNotEmpty() || klager.isNotEmpty()) {
            require(søknadsbehandlinger.isNotEmpty()) {
                "Søknadsbehandlinger må være satt hvis det finnes revurderinger, reguleringer eller klager."
            }
        }
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
