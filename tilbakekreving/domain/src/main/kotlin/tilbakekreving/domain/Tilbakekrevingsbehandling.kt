package tilbakekreving.domain

import arrow.core.NonEmptyList
import dokument.domain.Dokument
import dokument.domain.LagretDokumentHendelse
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.Månedsvurderinger
import java.time.Clock
import java.util.UUID

/**
 * Starter som [OpprettetTilbakekrevingsbehandling] & aksepterer kun 1 åpen behandling om gangen
 * Deretter tar de stilling til om hver måned i kravgrunnlaget skal tilbakekreves, eller ikke.
 * Vi får deretter en tilstand [VurdertTilbakekrevingsbehandling]
 *
 * @property versjon versjonen til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 * @property hendelseId hendelses iden til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 */
sealed interface Tilbakekrevingsbehandling {
    val id: TilbakekrevingsbehandlingId
    val sakId: UUID
    val opprettet: Tidspunkt
    val opprettetAv: NavIdentBruker.Saksbehandler
    val kravgrunnlag: Kravgrunnlag
    val månedsvurderinger: Månedsvurderinger?
    val brevvalg: Brevvalg.SaksbehandlersValg?
    val attesteringer: Attesteringshistorikk
    val versjon: Hendelsesversjon
    val hendelseId: HendelseId
    val forhåndsvarselDokumentIder: List<UUID>

    fun erÅpen(): Boolean

    /**
     * TODO - mulig vi burde lage et KanForhåndsvarsel interface, som tilstandene arver
     */
    fun leggTilForhåndsvarsel(
        command: ForhåndsvarselTilbakekrevingsbehandlingCommand,
        tidligereHendelsesId: HendelseId,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): Pair<ForhåndsvarsleTilbakekrevingsbehandlingHendelse, Tilbakekrevingsbehandling> =
        ForhåndsvarsleTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            meta = command.toDefaultHendelsesMetadata(),
            tidligereHendelseId = tidligereHendelsesId,
            id = command.behandlingId,
            utførtAv = command.utførtAv,
            fritekst = command.fritekst,
        ).let { it to this.applyHendelse(it) }

    /**
     * TODO - mulig vi burde lage et KanForhåndsvarsel interface, som tilstandene arver
     */
    fun leggTilForhåndsvarselDokumentId(dokumentId: UUID): Tilbakekrevingsbehandling

    /**
     * * TODO - mulig vi burde lage et KanForhåndsvarsel interface, som tilstandene arver
     * Denne lagrer nye dokumenter på det som er forhåndsvarsel. Dersom det skal være andre brev, må noe endres
     */
    fun lagreDokument(
        command: SakshendelseCommand,
        dokument: Dokument.MedMetadata,
        nesteVersjon: Hendelsesversjon,
        relaterteHendelser: NonEmptyList<HendelseId>,
        clock: Clock,
    ): Pair<LagretDokumentHendelse, Tilbakekrevingsbehandling> {
        return LagretDokumentHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            meta = command.toDefaultHendelsesMetadata(),
            sakId = command.sakId,
            relaterteHendelser = relaterteHendelser,
            dokument = dokument,
        ).let {
            it to this.applyHendelse(it)
        }
    }
}
