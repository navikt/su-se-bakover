package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentHendelseRepo
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsvarsle
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

class ForhåndsvarsleTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * TODO
     *  mulig vi burde lagre forhåndsvarsel hendelsen alene, og ha en jobb som gjør dokument genereringen
     */
    fun forhåndsvarsle(command: ForhåndsvarselTilbakekrevingsbehandlingCommand): Either<KunneIkkeForhåndsvarsle, Tilbakekrevingsbehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsvarsle.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling, fant ikke sak ${command.sakId}")
        }

        val behandling = sak.behandlinger.tilbakekrevinger.hent(command.behandlingId)
            ?: throw IllegalStateException("Fan ikke Tilbakekrevingsbehandling ${command.behandlingId}, Sak id ${sak.id}, saksnummer ${sak.saksnummer}")

        val (forhåndsvarsletHendelse, forhåndsvarsletBehandling) = behandling.leggTilForhåndsvarsel(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
        )

        val dokument = brevService.lagDokument(
            ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                fritekst = command.fritekst,
                saksbehandler = command.utførtAv,
            ),
        ).getOrElse {
            return KunneIkkeForhåndsvarsle.FeilVedDokumentGenerering(it).left()
        }.leggTilMetadata(Dokument.Metadata(sakId = sak.id, tilbakekrevingsbehandlingId = behandling.id.value))

        val (lagretDokumentHendelse, behandlingMedLagretDokument) = forhåndsvarsletBehandling.lagreDokument(
            command = command,
            dokument = dokument,
            nesteVersjon = forhåndsvarsletHendelse.versjon.inc(),
            relaterteHendelser = nonEmptyListOf(forhåndsvarsletHendelse.hendelseId),
            clock = clock,
        )

        val (tidligereOppgaveHendelse, oppgaveId) =
            tilbakekrevingsbehandlingRepo.hentForSak(sakId = command.sakId).hentOppgaveIdForBehandling(behandling.id)
                ?: throw IllegalStateException("Fant ikke oppgaveId som skal bli oppdatert for tilbakekreving ${behandling.id} for sak ${sak.id}")

        oppgaveService.oppdaterOppgave(oppgaveId = oppgaveId, beskrivelse = "Forhåndsvarsel er opprettet")
            .mapLeft {
                log.error("Kunne ikke oppdatere oppgave $oppgaveId for tilbakekreving ${behandling.id} med informasjon om at forhåndsvarsel er opprettet")
                sessionFactory.withTransactionContext {
                    tilbakekrevingsbehandlingRepo.lagre(forhåndsvarsletHendelse, it)
                    dokumentHendelseRepo.lagre(lagretDokumentHendelse, it)
                }
            }.map {
                val oppgaveHendelse = OppgaveHendelse.oppdatert(
                    hendelsestidspunkt = Tidspunkt.now(clock),
                    oppgaveId = oppgaveId,
                    versjon = sak.versjon.inc(3),
                    sakId = sak.id,
                    relaterteHendelser = listOf(forhåndsvarsletHendelse.hendelseId),
                    meta = command.toDefaultHendelsesMetadata(),
                    tidligereHendelseId = tidligereOppgaveHendelse.hendelseId,
                )

                sessionFactory.withTransactionContext {
                    tilbakekrevingsbehandlingRepo.lagre(forhåndsvarsletHendelse, it)
                    oppgaveHendelseRepo.lagre(oppgaveHendelse, it)
                    dokumentHendelseRepo.lagre(lagretDokumentHendelse, it)
                }
            }
        return behandlingMedLagretDokument.right()
    }
}
