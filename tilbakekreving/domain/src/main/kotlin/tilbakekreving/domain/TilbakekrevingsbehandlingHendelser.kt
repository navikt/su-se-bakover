package tilbakekreving.domain

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock
import java.util.UUID

data class TilbakekrevingsbehandlingHendelser private constructor(
    private val sakId: UUID,
    private val sorterteHendelser: List<TilbakekrevingsbehandlingHendelse>,
    private val kravgrunnlagPåSak: List<Kravgrunnlag>,
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

    companion object {

        fun empty(sakId: UUID, clock: Clock): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = emptyList(),
                kravgrunnlagPåSak = emptyList(),
            )
        }

        fun create(
            sakId: UUID,
            clock: Clock,
            hendelser: List<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<Kravgrunnlag>,
        ): TilbakekrevingsbehandlingHendelser {
            return TilbakekrevingsbehandlingHendelser(
                sakId = sakId,
                clock = clock,
                sorterteHendelser = hendelser.sorted(),
                kravgrunnlagPåSak = kravgrunnlagPåSak,
            )
        }

        private fun toCurrentState(
            sakId: UUID,
            hendelser: NonEmptyList<TilbakekrevingsbehandlingHendelse>,
            kravgrunnlagPåSak: List<Kravgrunnlag>,
        ): Tilbakekrevingsbehandlinger {
            return hendelser.fold(mapOf<HendelseId, Tilbakekrevingsbehandling>()) { acc, hendelse ->
                val hendelseId = hendelse.hendelseId
                when (hendelse) {
                    is OpprettetTilbakekrevingsbehandlingHendelse -> acc.plus(
                        hendelseId to hendelse.toDomain(
                            kravgrunnlag = kravgrunnlagPåSak.first { it.kravgrunnlagId == hendelse.kravgrunnlagsId },
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
                }
            }.values.toList().sortedBy { it.versjon }.let {
                Tilbakekrevingsbehandlinger(sakId, it)
            }
        }
    }
}
