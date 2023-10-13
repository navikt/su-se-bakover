package tilbakekreving.domain

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import java.time.Clock
import java.util.UUID

/**
 * @param tilhørendeOgSorterteOppgaveHendelser - oppgavehendelsene i denne listen må ha en tilbakekrevingshendelse i sin relaterteHendelser
 */
data class TilbakekrevingsbehandlingHendelser private constructor(
    private val sakId: UUID,
    private val sorterteHendelser: List<TilbakekrevingsbehandlingHendelse>,
    private val kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
    private val tilhørendeOgSorterteOppgaveHendelser: List<OppgaveHendelse>,
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
    }

    val currentState: Tilbakekrevingsbehandlinger by lazy {
        if (sorterteHendelser.isEmpty()) {
            Tilbakekrevingsbehandlinger.empty(sakId)
        } else {
            toCurrentState(
                sakId = sakId,
                hendelser = sorterteHendelser.toNonEmptyList(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
            )
        }
    }

    fun hentOppgaveId(): OppgaveId? = this.tilhørendeOgSorterteOppgaveHendelser.firstOrNull()?.oppgaveId

    companion object {

        fun empty(sakId: UUID, clock: Clock): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = emptyList(),
                kravgrunnlagPåSak = emptyList(),
                tilhørendeOgSorterteOppgaveHendelser = emptyList(),
            )
        }

        fun create(
            sakId: UUID,
            clock: Clock,
            hendelser: List<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
            oppgaveHendelser: List<OppgaveHendelse>,
        ): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
                tilhørendeOgSorterteOppgaveHendelser = oppgaveHendelser.sorted(),
            )
        }

        private fun toCurrentState(
            sakId: UUID,
            hendelser: NonEmptyList<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<KravgrunnlagPåSakHendelse>,
        ): Tilbakekrevingsbehandlinger {
            return hendelser.fold(mapOf<HendelseId, Tilbakekrevingsbehandling>()) { acc, hendelse ->
                val hendelseId = hendelse.hendelseId
                when (hendelse) {
                    is OpprettetTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to hendelse.toDomain(
                            kravgrunnlagPåSakHendelse = kravgrunnlagPåSak.first { it.kravgrunnlag.eksternKravgrunnlagId == hendelse.kravgrunnlagsId },
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
    }
}
