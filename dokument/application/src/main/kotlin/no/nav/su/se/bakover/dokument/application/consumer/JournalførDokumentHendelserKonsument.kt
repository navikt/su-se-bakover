package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokument
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class JournalførDokumentHendelserKonsument(
    private val sakService: SakService,
    private val journalførBrevClient: JournalførBrevClient,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("JournalførDokumentHendelserKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun journalførDokumenter(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = GenerertDokument,
        ).forEach { (sakId, hendelsesIder) ->
            journalførDokumenterForSak(
                sakId = sakId,
                hendelsesIder = hendelsesIder,
                correlationId = correlationId,
            )
        }
    }

    private fun journalførDokumenterForSak(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse {
                return Unit.also {
                    log.error("Feil under journalføring: Kunne ikke hente sak $sakId for hendelser $hendelsesIder")
                }
            }
        hendelsesIder.mapOneIndexed { index, hendelseId ->
            journalførDokumentForSak(sak, hendelseId, correlationId, sak.versjon.inc(index))
        }
    }

    private fun journalførDokumentForSak(
        sak: Sak,
        hendelseId: HendelseId,
        correlationId: CorrelationId,
        nesteVersjon: Hendelsesversjon,
    ) {
        val sakId = sak.id
        val dokumentHendelser = dokumentHendelseRepo.hentForSak(sakId)

        dokumentHendelser.any { it.relatertHendelse == hendelseId }.ifTrue {
            hendelsekonsumenterRepo.lagre(hendelseId, konsumentId)
            log.error("Prøvde å journalføre dokument som allerede er journalført. Sak $sakId, hendelse $hendelseId. Konsumenten har lagret denne hendelsen")
            return
        }

        val (generertDokumentHendelse, relatertFil) = hentDataForJournalføring(
            sak.info(),
            hendelseId,
        ).getOrElse { return }

        journalførDokument(
            sakInfo = sak.info(),
            generertDokumentHendelse = generertDokumentHendelse,
            relatertFil = relatertFil,
            versjon = nesteVersjon,
            skalSendeBrev = generertDokumentHendelse.skalSendeBrev,
        ).getOrElse {
            return
        }.let { journalførtDokumentHendelse ->
            sessionFactory.withSessionContext {
                dokumentHendelseRepo.lagreJournalførtDokumentHendelse(
                    hendelse = journalførtDokumentHendelse,
                    meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                    sessionContext = it,
                )
                hendelsekonsumenterRepo.lagre(hendelseId, konsumentId, it)
            }
        }
    }

    private fun hentDataForJournalføring(
        sakInfo: SakInfo,
        relatertHendelsesId: HendelseId,
    ): Either<Unit, Pair<GenerertDokumentHendelse, HendelseFil>> {
        val (relatertHendelse, relatertFil) = hentLagretDokumentHendelseForJournalføring(
            hendelseId = relatertHendelsesId,
            sakId = sakInfo.sakId,
        ).getOrElse { return Unit.left() }

        return Pair(relatertHendelse, relatertFil).right()
    }

    private fun journalførDokument(
        sakInfo: SakInfo,
        generertDokumentHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
        versjon: Hendelsesversjon,
        skalSendeBrev: Boolean,
    ): Either<Unit, JournalførtDokumentHendelse> = opprettJournalpost(
        sakInfo = sakInfo,
        relatertHendelse = generertDokumentHendelse,
        relatertFil = relatertFil,
    ).getOrElse {
        return Unit.left()
    }.let {
        JournalførtDokumentHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            sakId = sakInfo.sakId,
            relatertHendelse = generertDokumentHendelse.hendelseId,
            journalpostId = it,
            skalSendeBrev = skalSendeBrev,
        ).right()
    }

    private fun opprettJournalpost(
        sakInfo: SakInfo,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
    ): Either<Unit, JournalpostId> = journalførBrevClient.journalførBrev(
        JournalførBrevCommand(
            fnr = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            dokument = relatertHendelse.dokumentUtenFil.toDokumentMedMetadata(
                pdf = relatertFil.fil,
                // Vi har ikke/skal ikke journalføre/sende brev på dette tidspunktet.
                journalpostId = null,
                brevbestillingId = null,
            ),
            sakstype = sakInfo.type,
        ),
    ).mapLeft {
        Unit.also {
            log.error("Feil under journalføring: Kunne ikke opprette journalpost for hendelse ${relatertHendelse.hendelseId} og sak ${sakInfo.sakId}. Underliggende feil: $it")
        }
    }

    private fun hentLagretDokumentHendelseForJournalføring(
        hendelseId: HendelseId,
        sakId: UUID,
    ): Either<Unit, Pair<GenerertDokumentHendelse, HendelseFil>> {
        return dokumentHendelseRepo.hentHendelseOgFilFor(hendelseId).let {
            val assertedHendelse: GenerertDokumentHendelse = when (val d = it.first) {
                is GenerertDokumentHendelse -> d
                is JournalførtDokumentHendelse -> return Unit.left().also {
                    log.error("Feil under journalføring: GenerertDokumentHendelse $hendelseId var ikke av typen JournalførtDokumentHendelse. Sak $sakId")
                }

                is DistribuertDokumentHendelse -> return Unit.left().also {
                    log.error("Feil under journalføring: Hendelse $hendelseId er journalført og distribuert fra før, for sak $sakId")
                }

                null -> return Unit.left().also {
                    log.error("Feil under journalføring: Fant ikke dokument med hendelse $hendelseId for sak $sakId")
                }
            }
            val assertedFil = when (val f = it.second) {
                null -> return Unit.left()
                    .also { log.error("Feil under journalføring: Fant ikke tilhørende fil for GenerertDokumentHendelse $hendelseId og sak sak $sakId") }

                else -> f
            }
            Pair(assertedHendelse, assertedFil).right()
        }
    }
}
