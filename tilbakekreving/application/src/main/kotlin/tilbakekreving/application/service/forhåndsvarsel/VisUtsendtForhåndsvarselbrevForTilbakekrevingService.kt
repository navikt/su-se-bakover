package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.PdfA
import org.slf4j.LoggerFactory
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeHenteUtsendtForhåndsvarsel
import tilbakekreving.domain.forhåndsvarsel.VisUtsendtForhåndsvarselbrevCommand
import tilgangstyring.application.TilgangstyringService

class VisUtsendtForhåndsvarselbrevForTilbakekrevingService(
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val tilgangstyring: TilgangstyringService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hent(command: VisUtsendtForhåndsvarselbrevCommand): Either<KunneIkkeHenteUtsendtForhåndsvarsel, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).getOrElse {
            return KunneIkkeHenteUtsendtForhåndsvarsel.IkkeTilgang(it).left()
        }

        val (dokument, fil) = dokumentHendelseRepo.hentHendelseOgFilForDokumentId(command.dokumentId)
        if (dokument == null || fil == null || dokument.sakId != command.sakId) {
            log.info(
                "Fant ikke forhåndsvarseldokument på oppgitt sak. sakId={} dokumentId={}",
                command.sakId,
                command.dokumentId,
            )
            return KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument.left()
        }
        return fil.fil.right()
    }
}
