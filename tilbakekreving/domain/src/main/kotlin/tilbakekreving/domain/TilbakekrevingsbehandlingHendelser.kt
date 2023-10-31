package tilbakekreving.domain

import dokument.domain.hendelser.DokumentHendelse
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.pickByCondition
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelser
import java.time.Clock
import java.util.UUID

/**
 * @param tilhørendeOgSorterteOppgaveHendelser - oppgavehendelsene må ha en tilbakekrevingshendelse i sin relaterteHendelser
 * @param tilhørendeOgSorterteDokumentHendelser - Dokumenthendelsen må ha en tilbakekrevingshendelse i sin relaterteHendelser
 */
data class TilbakekrevingsbehandlingHendelser private constructor(
    private val sakId: UUID,
    private val sorterteHendelser: List<TilbakekrevingsbehandlingHendelse>,
    private val kravgrunnlagPåSak: KravgrunnlagPåSakHendelser,
    private val tilhørendeOgSorterteOppgaveHendelser: List<OppgaveHendelse>,
    private val tilhørendeOgSorterteDokumentHendelser: List<DokumentHendelse>,
    private val clock: Clock,
) : List<TilbakekrevingsbehandlingHendelse> by sorterteHendelser {
    init {
        require(sorterteHendelser.sorted() == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser må være sortert etter stigende versjon."
        }
        require(sorterteHendelser.distinctBy { it.hendelseId } == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser kan ikke ha duplikat hendelseId."
        }
        require(sorterteHendelser.distinctBy { it.versjon } == sorterteHendelser) {
            "TilbakekrevingsbehandlingHendelser kan ikke ha duplikat versjon."
        }

        sorterteHendelser.groupBy { it.id }.forEach {
            require(it.value.first() is OpprettetTilbakekrevingsbehandlingHendelse) {
                "Den første hendelsen må være en opprettet hendelse"
            }
            it.value.filterIsInstance<OpprettetTilbakekrevingsbehandlingHendelse>().let {
                require(it.size == 1) {
                    "Den første hendelse (og kun den første) må være en OpprettetTilbakekrevingsbehandlingHendelse, men fant: ${it.size}"
                }
            }
        }

        sorterteHendelser.mapNotNull { it.tidligereHendelseId }.let {
            require(it.distinct() == it) {
                "En hendelse kan kun bli endret en gang. Oppdaget duplikate tidligereHendelseId: ${
                    it.groupBy { it }.filter { it.value.size > 1 }.values
                }"
            }
        }
        require(sorterteHendelser.map { it.sakId }.distinct().size <= 1) {
            "TilbakekrevingsbehandlingHendelser kan kun være knyttet til én sak, men var: ${
                sorterteHendelser.map { it.sakId }.distinct()
            }"
        }
        require(sorterteHendelser.map { it.entitetId }.distinct().size <= 1) {
            "TilbakekrevingsbehandlingHendelser kan kun være knyttet til én enitetId (samme som sakId), men var: ${
                sorterteHendelser.map { it.entitetId }.distinct()
            }"
        }
        require(tilhørendeOgSorterteOppgaveHendelser.sorted() == tilhørendeOgSorterteOppgaveHendelser) {
            "tilhørendeOgSorterteOppgaveHendelser må være sortert etter stigende versjon."
        }

        require(
            tilhørendeOgSorterteOppgaveHendelser.all { oppgaveHendelse ->
                sorterteHendelser.any { opprettetHendelse ->
                    oppgaveHendelse.relaterteHendelser.contains(opprettetHendelse.hendelseId)
                }
            },
        ) { "Oppgavehendelsene må være relatert til minst 1 tilbakekrevingsbehandlingHendelse" }

        require(
            tilhørendeOgSorterteDokumentHendelser.all { dokumentHendelse ->
                sorterteHendelser.any { opprettetHendelse ->
                    dokumentHendelse.relaterteHendelser.contains(opprettetHendelse.hendelseId)
                }
            },
        ) { "DokumentHendelse må være relatert til minst 1 tilbakekrevingsbehandlingHendelse" }
    }

    val currentState: Tilbakekrevingsbehandlinger by lazy {
        if (sorterteHendelser.isEmpty()) {
            Tilbakekrevingsbehandlinger.empty(sakId)
        } else {
            toCurrentState()
        }
    }

    private fun toCurrentState(): Tilbakekrevingsbehandlinger {
        return this.fold(mapOf<HendelseId, Tilbakekrevingsbehandling>()) { acc, hendelse ->
            val hendelseId = hendelse.hendelseId
            when (hendelse) {
                // Dette gjelder kun første hendelsen og er et spesialtilfelle.
                is OpprettetTilbakekrevingsbehandlingHendelse -> acc.plus(
                    hendelseId to hendelse.toDomain(
                        kravgrunnlagPåSakHendelse = this.kravgrunnlagPåSak.first { it.kravgrunnlag.eksternKravgrunnlagId == hendelse.kravgrunnlagsId },
                    ),
                )

                else -> acc.plus(
                    hendelseId to hendelse.applyToState(acc[hendelse.tidligereHendelseId!!]!!),
                ).minus(hendelse.tidligereHendelseId!!)
            }
        }.values.toList().sortedBy { it.versjon }.let {
            Tilbakekrevingsbehandlinger(this.sakId, it)
        }
    }

    fun hentOppgaveIdForBehandling(id: TilbakekrevingsbehandlingId): Pair<OppgaveHendelse, OppgaveId>? {
        val hendelsesIderForBehandling = sorterteHendelser.filter { it.id == id }.map { it.hendelseId }

        val oppgaveHendelserForBehandling =
            tilhørendeOgSorterteOppgaveHendelser.pickByCondition(hendelsesIderForBehandling) { oppgaveHendelse, hendelseId ->
                oppgaveHendelse.relaterteHendelser.contains(hendelseId)
            }

        return oppgaveHendelserForBehandling.whenever(
            isEmpty = { null },
            isNotEmpty = { it.max() to it.max().oppgaveId },
        )
    }

    /**
     * @throws IllegalArgumentException dersom et av init-kravene feiler.
     */
    fun leggTil(nyHendelse: TilbakekrevingsbehandlingHendelse): TilbakekrevingsbehandlingHendelser {
        return this.copy(
            sorterteHendelser = sorterteHendelser + nyHendelse,
        )
    }

    fun hentSerieFor(behandlingsid: TilbakekrevingsbehandlingId): TilbakekrevingbehandlingsSerie =
        TilbakekrevingbehandlingsSerie(
            sakId = sakId,
            behandlingsId = behandlingsid,
            hendelser = sorterteHendelser.filter { it.id == behandlingsid },
        )

    companion object {

        fun empty(sakId: UUID, clock: Clock): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = emptyList(),
                kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(emptyList()),
                tilhørendeOgSorterteOppgaveHendelser = emptyList(),
                tilhørendeOgSorterteDokumentHendelser = emptyList(),
            )
        }

        fun create(
            sakId: UUID,
            clock: Clock,
            hendelser: List<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
            oppgaveHendelser: List<OppgaveHendelse>,
            dokumentHendelser: List<DokumentHendelse>,
        ): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
                kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(kravgrunnlagPåSak),
                tilhørendeOgSorterteOppgaveHendelser = oppgaveHendelser.sorted(),
                tilhørendeOgSorterteDokumentHendelser = dokumentHendelser.sorted(),
            )
        }
    }
}
