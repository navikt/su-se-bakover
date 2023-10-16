package tilbakekreving.domain

import arrow.core.NonEmptyList
import dokument.domain.LagretDokumentHendelse
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.pickByCondition
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import java.time.Clock
import java.util.UUID

/**
 * @param tilhørendeOgSorterteOppgaveHendelser - oppgavehendelsene må ha en tilbakekrevingshendelse i sin relaterteHendelser
 * @param tilhørendeOgSorterteDokumentHendelser - Dokumenthendelsen må ha en tilbakekrevingshendelse i sin relaterteHendelser
 */
data class TilbakekrevingsbehandlingHendelser private constructor(
    private val sakId: UUID,
    private val sorterteHendelser: List<TilbakekrevingsbehandlingHendelse>,
    private val kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
    private val tilhørendeOgSorterteOppgaveHendelser: List<OppgaveHendelse>,
    private val tilhørendeOgSorterteDokumentHendelser: List<LagretDokumentHendelse>,
    private val clock: Clock,
) {
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
            toCurrentState(
                sakId = sakId,
                hendelser = sorterteHendelser.toNonEmptyList(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
                dokumentHendelser = tilhørendeOgSorterteDokumentHendelser,
            )
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

    companion object {

        fun empty(sakId: UUID, clock: Clock): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = emptyList(),
                kravgrunnlagPåSak = emptyList(),
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
            dokumentHendelser: List<LagretDokumentHendelse>,
        ): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
                tilhørendeOgSorterteOppgaveHendelser = oppgaveHendelser.sorted(),
                tilhørendeOgSorterteDokumentHendelser = dokumentHendelser.sorted(),
            )
        }

        private fun toCurrentState(
            sakId: UUID,
            hendelser: NonEmptyList<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
            dokumentHendelser: List<LagretDokumentHendelse>,
        ): Tilbakekrevingsbehandlinger {
            return hendelser.fold(mapOf<HendelseId, Tilbakekrevingsbehandling>()) { acc, hendelse ->
                val hendelseId = hendelse.hendelseId
                when (hendelse) {
                    is OpprettetTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to hendelse.toDomain(
                            kravgrunnlagPåSakHendelse = kravgrunnlagPåSak.first { it.kravgrunnlag.eksternKravgrunnlagId == hendelse.kravgrunnlagsId },
                            // Id'ene må legges inn på et tidspunkt
                            forhåndsvarselDokumentIder = hendelse.hentRelaterteDokumenter(hendelser, dokumentHendelser)
                                .map { it.dokument.id },
                        ),
                    )

                    is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyHendelse(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)

                    is BrevTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyHendelse(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)

                    is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyHendelse(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)

                    is TilAttesteringHendelse -> acc.plus(
                        hendelseId to acc[hendelse.tidligereHendelseId]!!.applyHendelse(
                            hendelse,
                        ),
                    ).minus(hendelse.tidligereHendelseId)
                }
            }.values.toList().sortedBy { it.versjon }.let {
                Tilbakekrevingsbehandlinger(sakId, it)
            }
        }

        private fun List<TilbakekrevingsbehandlingHendelse>.lagHendelsesSerier(): List<List<TilbakekrevingsbehandlingHendelse>> {
            val sortertOpprettetHendelser = this.filterIsInstance<OpprettetTilbakekrevingsbehandlingHendelse>().sorted()

            return sortertOpprettetHendelser
                .mapIndexed { index, currentEvent ->
                    val nesteHendelsesVersjon = sortertOpprettetHendelser.getOrNull(index + 1)?.versjon
                        ?: this.max().versjon

                    this.filter { it.versjon in currentEvent.versjon..nesteHendelsesVersjon }
                }
        }

        private fun NonEmptyList<TilbakekrevingsbehandlingHendelse>.hentSerieFor(tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId): List<TilbakekrevingsbehandlingHendelse> {
            return this.lagHendelsesSerier().first { it.any { it.id == tilbakekrevingsbehandlingId } }
        }

        private fun TilbakekrevingsbehandlingHendelse.hentRelaterteDokumenter(
            hendelser: NonEmptyList<TilbakekrevingsbehandlingHendelse>,
            dokumenter: List<LagretDokumentHendelse>,
        ): List<LagretDokumentHendelse> {
            return hendelser.hentRelaterteDokumenter(this.id, dokumenter)
        }

        private fun NonEmptyList<TilbakekrevingsbehandlingHendelse>.hentRelaterteDokumenter(
            tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId,
            dokumenter: List<LagretDokumentHendelse>,
        ): List<LagretDokumentHendelse> {
            val seriensHendelsesIder = hentSerieFor(tilbakekrevingsbehandlingId).map { it.hendelseId }

            return dokumenter.pickByCondition(seriensHendelsesIder) { dokumentHendelse, tilbakekrevingsbehandlingHendelseId ->
                dokumentHendelse.relaterteHendelser.contains(tilbakekrevingsbehandlingHendelseId)
            }
        }
    }
}
