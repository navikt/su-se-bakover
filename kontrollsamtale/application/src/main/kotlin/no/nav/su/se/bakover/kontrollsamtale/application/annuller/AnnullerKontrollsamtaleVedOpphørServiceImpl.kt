package no.nav.su.se.bakover.kontrollsamtale.application.annuller

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Definerer kontrakten fra behandling til kontrollsamtale.
 * Ideélt sett bør det heller sendes en hendelse om et nytt stønadsvedtak på saken (sakId).
 * Også bør kontrollsamtale hente gjeldende vedtak/stønad for saken på nytt og ta en ny avgjørelse basert på dette.
 */
class AnnullerKontrollsamtaleVedOpphørServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleServiceImpl,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
) : AnnullerKontrollsamtaleVedOpphørService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun annuller(
        // TODO jah: Bør være en extension-function: Sak.annullerKontrollsamtale(...),
        //  så lenge ikke kontrollsamtaler ligger som data på saken.
        //  I siste tilfellet bør vi ta i mot en sak.
        sakId: UUID,
        sessionContext: SessionContext,
    ) {
        // TODO jah: Bør vurdere om vi skal legge kontrollsamtaler på Sak (da kan ikke kontrollsamtale:domain ha referanser til :domain)
        // TODO jah: Bør hente alle kontrollsamtaler knyttet til saken og gjøre en litt grundigere vurdering per tilfelle.
        return kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sakId, sessionContext).fold(
            {
                log.info("Trenger ikke annullere kontrollsamtale, siden det er ingen planlagt kontrollsamtale for sakId $sakId")
            },
            { kontrollsamtale ->
                kontrollsamtale.annuller().map { annullertKontrollSamtale ->
                    kontrollsamtaleRepo.lagre(annullertKontrollSamtale, sessionContext)
                }.mapLeft {
                    // hentNestePlanlagteKontrollsamtale(..) returnerer kun Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    // som er en gyldig overgang. Så lenge det ikke endrer seg (tester?) er det trygt å kaste her.
                    // Iverksett revurdering er avhengig av at denne kaster for at den skal kunne rulle tilbake transaksjonen.
                    throw IllegalStateException("Kunne ikke annullere kontrollsamtale ${kontrollsamtale.id} med status ${kontrollsamtale.status}. Underliggende feil: $it")
                }
            },
        )
    }
}
