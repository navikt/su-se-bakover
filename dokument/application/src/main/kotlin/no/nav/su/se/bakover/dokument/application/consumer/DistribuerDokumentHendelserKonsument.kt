package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeDistribuereJournalførtDokument
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.JournalførtDokument
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
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
            distribuerForSakOgHendelser(sakId = sakId, hendelsesIder = hendelsesIder, correlationId = correlationId)
        }
    }

    private fun distribuerForSakOgHendelser(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        sakService.hentSak(sakId).fold(
            { log.error("Feil under distribuering: Kunne ikke hente sak $sakId for hendelser $hendelsesIder") },
            {
                hendelsesIder.mapOneIndexed { index, hendelseId ->
                    Either.catch {
                        distribuerForSak(it, hendelseId, correlationId, it.versjon.inc(index))
                    }.onLeft {
                        log.error(
                            "Feil under distribuering: Se sikkerlogg for mer context.",
                            RuntimeException("Trigger stacktrace for debug."),
                        )
                        sikkerLogg.error("Feil under distribuering: Se sikkerlogg for mer context.", it)
                    }
                }
            },
        )
    }

    fun ditribuerForSakId(
        sakId: UUID,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeDistribuereJournalførtDokument, DistribuertDokumentHendelse> {
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalArgumentException("Feil under distribuering: Kunne ikke hente sak $sakId for hendelse $hendelseId")
        }
        return distribuerForSak(sak, hendelseId, correlationId, sak.versjon.inc(), distribueringsadresse)
    }

    private fun distribuerForSak(
        sak: Sak,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
        nesteVersjon: Hendelsesversjon,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeDistribuereJournalførtDokument, DistribuertDokumentHendelse> {
        val sakId = sak.id
        val saksnummer = sak.saksnummer
        val dokumentHendelser = dokumentHendelseRepo.hentDokumentHendelserForSakId(sakId)

        val serie = dokumentHendelser.hentSerieForHendelseId(hendelseId)
            ?: throw IllegalStateException("Feil under distribuering: Fant ikke dokumentserie. Konsumenten vil prøvde denne på nytt. hendelseId: $hendelseId sakId: $sakId, saksnummer: $saksnummer")

        val dokumentId = serie.dokumentId
        if (!serie.harJournalført()) {
            log.error("Feil under distribuering: Prøvde å distribuere et dokument som ikke er journalført. Konsumenten vil prøvde denne på nytt. Dette må ryddes opp i manuelt. hendelseId: $hendelseId sakId: $sakId, saksnummer: $saksnummer, dokumentId: $dokumentId")
            return KunneIkkeDistribuereJournalførtDokument.IkkeJournalført(
                dokumentId = dokumentId,
            ).left()
        }
        val journalpostId = serie.journalpostHendelseOrNull()!!.journalpostId
        if (serie.harBestiltBrev()) {
            hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
            log.error("Feil under distribuering: Prøvde å distribuere et dokument som allerede er distribuert. Konsumenten vil ikke prøve denne på nytt. hendelseId: $hendelseId sakId: $sakId, saksnummer: $saksnummer, dokumentId: $dokumentId, journalpostId: $journalpostId")
            return KunneIkkeDistribuereJournalførtDokument.AlleredeDistribuert(
                dokumentId = dokumentId,
                journalpostId = journalpostId,
                brevbestillingId = serie.brevbestillingIdOrNull()!!,
            ).left()
        }

        val generertDokumentHendelse = serie.generertDokument()
        val journalførtDokumentHendelse = serie.journalpostHendelseOrNull()!!

        if (!generertDokumentHendelse.skalSendeBrev) {
            log.info("Distribuering: Det skal ikke sendes brev for dette dokumentet. Konsumenten vil ikke prøve denne på nytt. hendelseId: $hendelseId sakId: $sakId, saksnummer: $saksnummer, dokumentId: $dokumentId, journalpostId: $journalpostId")
            hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
            return KunneIkkeDistribuereJournalførtDokument.SkalIkkeSendeBrev(
                dokumentId = dokumentId,
                journalpostId = journalpostId,
            ).left()
        }
        return dokDistFordeling.bestillDistribusjon(
            journalPostId = journalpostId,
            distribusjonstype = generertDokumentHendelse.dokumentUtenFil.distribusjonstype,
            distribusjonstidspunkt = generertDokumentHendelse.dokumentUtenFil.distribusjonstidspunkt,
            distribueringsadresse = distribueringsadresse,
        ).mapLeft {
            log.error("Feil under distribuering: Klientfeil. Konsumenten vil ikke prøve denne på nytt. hendelseId: $hendelseId sakId: $sakId, saksnummer: $saksnummer, dokumentId: $dokumentId, journalpostId: $journalpostId.")
            KunneIkkeDistribuereJournalførtDokument.FeilVedDistribusjon(
                dokumentId,
                journalpostId,
            )
        }.map {
            DistribuertDokumentHendelse(
                hendelseId = HendelseId.generer(),
                hendelsestidspunkt = Tidspunkt.now(clock),
                versjon = nesteVersjon,
                sakId = sakId,
                relatertHendelse = journalførtDokumentHendelse.hendelseId,
                brevbestillingId = it,
            ).also { distribuertDokumentHendelse ->
                sessionFactory.withSessionContext { tx ->
                    dokumentHendelseRepo.lagreDistribuertDokumentHendelse(
                        hendelse = distribuertDokumentHendelse,
                        meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                        sessionContext = tx,
                    )
                    hendelsekonsumenterRepo.lagre(hendelseId, konsumentId, tx)
                }
            }
        }
    }
}
