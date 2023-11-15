package tilbakekreving.domain

import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

/**
 * Det som gjør denne ulik [TilbakekrevingsbehandlingHendelser], er at denne inneholder kun hendelser for en gitt behandling, og ikke på tvers av behandlinger
 */
data class TilbakekrevingbehandlingsSerie(
    private val sakId: UUID,
    private val behandlingsId: TilbakekrevingsbehandlingId,
    private val hendelser: List<TilbakekrevingsbehandlingHendelse>,
) {
    init {
        require(hendelser.map { it.sakId }.distinct().single() == sakId) {
            "En TilbakekrevingbehandlingsSerie, kan kun være innenfor en enkel sak"
        }
        require(hendelser.distinctBy { it.hendelseId } == hendelser) {
            "TilbakekrevingbehandlingsSerie kan ikke ha duplikat hendelseId."
        }
        require(hendelser.distinctBy { it.versjon } == hendelser) {
            "TilbakekrevingbehandlingsSerie kan ikke ha duplikat versjon."
        }
        require(hendelser.filterIsInstance<OpprettetTilbakekrevingsbehandlingHendelse>().size == 1) {
            "En TilbakekrevingbehandlingsSerie kan kun inneholde 1 OpprettetTilbakekrevingsbehandlingHendelse"
        }
        require(hendelser.map { it.entitetId }.distinct().size <= 1) {
            "TilbakekrevingbehandlingsSerie kan kun være knyttet til én enitetId (samme som sakId)"
        }
        hendelser.mapNotNull { it.tidligereHendelseId }.let {
            require(it.distinct() == it) {
                "En hendelse kan kun bli endret en gang. Oppdaget duplikate tidligereHendelseId"
            }
        }
    }

    fun hendelsesIder(): Set<HendelseId> = hendelser.map { it.hendelseId }.toSet()
}
