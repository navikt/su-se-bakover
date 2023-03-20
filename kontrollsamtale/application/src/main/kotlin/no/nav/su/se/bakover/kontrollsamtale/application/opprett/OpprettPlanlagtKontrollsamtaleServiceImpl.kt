package no.nav.su.se.bakover.kontrollsamtale.application.opprett

import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import java.time.Clock

/**
 * Definerer kontrakten fra behandling til kontrollsamtale.
 * Ideélt sett bør det heller sendes en hendelse om et nytt stønadsvedtak på saken (sakId).
 * Også bør kontrollsamtale vurdere saken i sin helhet og gjøre en ny totalvurdering av kontrollsamtaler.
 */
class OpprettPlanlagtKontrollsamtaleServiceImpl(
    private val kontrollsamtaleService: KontrollsamtaleServiceImpl,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val clock: Clock,
) : OpprettKontrollsamtaleVedNyStønadsperiodeService {

    override fun opprett(
        vedtak: VedtakInnvilgetSøknadsbehandling,
        sessionContext: SessionContext,
    ) {
        // hentNestePlanlagteKontrollsamtale(...) henter kun PLANLAGT_INNKALLING
        // Dersom det f.eks. akkurat har blitt sendt ut et kontrollsamtale-brev (innkalt), vil vi her lage et nytt.
        // TODO jah: Gjør en vurdering på om dette er greit nok, eller bør gjøres om.
        return kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sakId = vedtak.behandling.sakId,
            sessionContext = sessionContext,
        ).fold(
            {
                Kontrollsamtale.opprettNyKontrollsamtaleFraVedtak(vedtak, clock).fold(
                    {
                        log.info("Skal ikke planlegge kontrollsamtale for sak ${vedtak.behandling.sakId} og vedtak ${vedtak.id}")
                    },
                    {
                        log.info("Opprettet planlagt kontrollsamtale $it for vedtak ${vedtak.id}")
                        kontrollsamtaleRepo.lagre(it, sessionContext)
                    },
                )
            },
            {
                // TODO jah: Her tenker jeg det er stor sannsynlighet for at vi ønsker å endre datoen på den eksisterende planlaget kontrollsamtalen.
                log.info("Planlagt kontrollsamtale finnes allerede for sak ${vedtak.behandling.sakId} og vedtak ${vedtak.id}")
            },
        )
    }
}
