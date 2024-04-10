package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.extensions.isFirstNull
import no.nav.su.se.bakover.common.domain.extensions.isSecondNull
import no.nav.su.se.bakover.common.domain.wheneverEitherIsNull
import org.slf4j.LoggerFactory
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeHenteUtsendtForhåndsvarsel
import tilbakekreving.domain.forhåndsvarsel.VisUtsendtForhåndsvarselbrevCommand

class VisUtsendtForhåndsvarselbrevForTilbakekrevingService(
    private val dokumentHendelseRepo: DokumentHendelseRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hent(command: VisUtsendtForhåndsvarselbrevCommand): Either<KunneIkkeHenteUtsendtForhåndsvarsel, PdfA> {
        val dokumentOgFil = dokumentHendelseRepo.hentHendelseOgFilForDokument(command.dokumentId)

        return dokumentOgFil.wheneverEitherIsNull(
            {
                KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument.left().also {
                    log.error("Fant ikke forhåndsvarsel dokument for utsendelse for behandling ${command.tilbakekrevingsbehandlingId} sak ${command.sakId}, dokument ${command.dokumentId}. ${if (dokumentOgFil.isFirstNull()) "dokumentHendelse fantes ikke" else "Dokument hendelse fantes"}. ${if (dokumentOgFil.isSecondNull()) "HendelseFil fantes ikke" else "HendelseFil fantes"}.")
                }
            },
            { (_, fil) ->
                fil.fil.right()
            },
        )
    }
}
