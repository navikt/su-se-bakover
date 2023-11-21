package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.Nel
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokument
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class DistribuerDokumentHendelserKonsument(
    private val sakService: SakService,
    private val dokDistFordeling: DokDistFordeling,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("DistribuerDokumentHendelserKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun distribuer(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = JournalførtDokument,
        ).forEach { (sakId, hendelsesIder) ->
            distribuerForSak(sakId = sakId, hendelsesIder = hendelsesIder, correlationId = correlationId)
        }
    }

    private fun distribuerForSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        sakService.hentSak(sakId).fold(
            { log.error("Feil under distribuering: Kunne ikke hente sak $sakId for hendelser $hendelsesIder") },
            {
                hendelsesIder.mapOneIndexed { index, hendelseId ->
                    distribuerForSak(it, hendelseId, correlationId, it.versjon.inc(index))
                }
            },
        )
    }

    private fun distribuerForSak(
        sak: Sak,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
        nesteVersjon: Hendelsesversjon,
    ) {
        val lagretHendelse = dokumentHendelseRepo.hentHendelse(hendelseId) ?: return Unit.also {
            log.error("Feil under distribuering: Kunne ikke hente hendelse $hendelseId for sak $sak")
        }

        when (lagretHendelse) {
            is GenerertDokumentHendelse -> return Unit.also {
                log.error("Prøvde å distribuere et generert dokument hendelse $lagretHendelse, for sak $sak. Denne må journalføres først.")
            }

            is JournalførtDokumentHendelse -> {
                if (!lagretHendelse.skalSendeBrev) {
                    hendelsekonsumenterRepo.lagre(lagretHendelse.hendelseId, konsumentId)
                }

                val relaterteHendelseForJournalførteDokument =
                    (dokumentHendelseRepo.hentHendelse(lagretHendelse.relatertHendelse) as? GenerertDokumentHendelse)
                        ?: return Unit.also { log.error("Fant ikke relaterte hendelse, eller var ikke i riktig tilstand, for ${lagretHendelse.hendelseId} for sak ${sak.id}") }

                dokDistFordeling.bestillDistribusjon(
                    journalPostId = lagretHendelse.journalpostId,
                    distribusjonstype = relaterteHendelseForJournalførteDokument.dokumentUtenFil.distribusjonstype,
                    distribusjonstidspunkt = relaterteHendelseForJournalførteDokument.dokumentUtenFil.distribusjonstidspunkt,
                ).fold(
                    { log.error("Feil ved distribuering av journalført dokument for hendelse $hendelseId. Original feil: $it") },
                    {
                        DistribuertDokumentHendelse(
                            hendelseId = HendelseId.generer(),
                            hendelsestidspunkt = Tidspunkt.now(clock),
                            versjon = nesteVersjon,
                            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                            sakId = sak.id,
                            relatertHendelse = lagretHendelse.hendelseId,
                            brevbestillingId = it,
                        ).let { distribuertDokumentHendelse ->
                            sessionFactory.withSessionContext { tx ->
                                dokumentHendelseRepo.lagre(distribuertDokumentHendelse, tx)
                                hendelsekonsumenterRepo.lagre(lagretHendelse.hendelseId, konsumentId, tx)
                            }
                        }
                    },
                )
            }

            is DistribuertDokumentHendelse -> return Unit.also {
                log.error("Feil ved distribuering av journalført dokument. Dokumentet er allerede distribuert. Hendelse $hendelseId, for sak ${sak.id}")
            }
        }
    }
}
