package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentForArkiveringHendelse
import dokument.domain.hendelser.GenerertDokumentForJournalføring
import dokument.domain.hendelser.GenerertDokumentForJournalføringHendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelseHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import person.domain.Person
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class JournalførDokumentHendelserKonsument(
    private val sakService: SakService,
    private val personService: PersonService,
    private val dokArkiv: DokArkiv,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val hendelseRepo: HendelseRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : Hendelseskonsument {
    override val konsumentId = HendelseskonsumentId("JournalførDokumentHendelserKonsument")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun journalførDokumenter(correlationId: CorrelationId) {
        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = GenerertDokumentForJournalføring,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSakForArkiveringAvJournalførtDokument(
                sakId = sakId,
                hendelsesIder = hendelsesIder,
                correlationId = correlationId,
            )
        }

        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = GenerertDokumentForUtsendelse,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSakForUtsendelseAvJournalførtDokument(
                sakId = sakId,
                hendelsesIder = hendelsesIder,
                correlationId = correlationId,
            )
        }
    }

    private fun prosesserSakForArkiveringAvJournalførtDokument(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse { return Unit.also { log.error("Kunne ikke hente sak $sakId for hendelser $hendelsesIder for å journalføre dokument for arkivering") } }

        hendelsesIder.mapOneIndexed { idx, relatertHendelsesId ->
            val dokumentHendelser = dokumentHendelseRepo.hentForSak(sak.id)

            dokumentHendelser.any { it.relaterteHendelser.contains(relatertHendelsesId) }.ifTrue {
                hendelsekonsumenterRepo.lagre(relatertHendelsesId, konsumentId)
                return@mapOneIndexed
            }

            val (relatertHendelse, relatertFil, navn) = hentDataForJournalføringAvDokument(
                sak.info(),
                relatertHendelsesId,
            ).getOrElse { return@mapOneIndexed }

            opprettJournalpostForArkivering(
                sakInfo = sak.info(),
                navn = navn,
                relatertHendelse = relatertHendelse,
                relatertFil = relatertFil,
                versjon = sak.versjon.inc(idx),
                correlationId = correlationId,
            ).getOrElse {
                return@mapOneIndexed
            }.let { forArkivering ->
                sessionFactory.withSessionContext {
                    dokumentHendelseRepo.lagre(forArkivering, it)
                    hendelsekonsumenterRepo.lagre(relatertHendelsesId, konsumentId, it)
                }
            }
        }
    }

    private fun prosesserSakForUtsendelseAvJournalførtDokument(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sak = sakService.hentSak(sakId)
            .getOrElse { return Unit.also { log.error("Kunne ikke hente sak $sakId for hendelser $hendelsesIder for å journalføre dokument for utsendelse") } }

        hendelsesIder.mapOneIndexed { idx, relatertHendelsesId ->
            val dokumentHendelser = dokumentHendelseRepo.hentForSak(sak.id)

            dokumentHendelser.any { it.relaterteHendelser.contains(relatertHendelsesId) }.ifTrue {
                hendelsekonsumenterRepo.lagre(relatertHendelsesId, konsumentId)
                return@mapOneIndexed
            }

            val (relatertHendelse, relatertFil, navn) = hentDataForJournalføringAvDokument(
                sak.info(),
                relatertHendelsesId,
            ).getOrElse { return@mapOneIndexed }

            opprettJournalpostForUtsendelse(
                sakInfo = sak.info(),
                navn = navn,
                relatertHendelse = relatertHendelse,
                relatertFil = relatertFil,
                versjon = sak.versjon.inc(idx),
                correlationId = correlationId,
            ).getOrElse { return@mapOneIndexed }.let { forUtsendelse ->
                sessionFactory.withSessionContext {
                    dokumentHendelseRepo.lagre(forUtsendelse, it)
                    hendelsekonsumenterRepo.lagre(relatertHendelsesId, konsumentId, it)
                }
            }
        }
    }

    private fun hentDataForJournalføringAvDokument(
        sakInfo: SakInfo,
        relatertHendelsesId: HendelseId,
    ): Either<Unit, Triple<GenerertDokumentHendelse, HendelseFil, Person.Navn>> {
        val (relatertHendelse, relatertFil) = hentLagretDokumentHendelseForJournalføring(relatertHendelsesId).getOrElse { return Unit.left() }

        val person = personService.hentPersonMedSystembruker(sakInfo.fnr).getOrElse {
            return Unit.left().also {
                log.error("Feil ved henting av person for journalføring av dokument for hendelse ${relatertHendelse.hendelseId} for sak ${sakInfo.sakId}")
            }
        }

        return Triple(relatertHendelse, relatertFil, person.navn).right()
    }

    private fun opprettJournalpostForArkivering(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
        versjon: Hendelsesversjon,
        correlationId: CorrelationId,
    ): Either<Unit, JournalførtDokumentForArkiveringHendelse> = opprettJournalpost(
        sakInfo = sakInfo,
        navn = navn,
        relatertHendelse = relatertHendelse,
        relatertFil = relatertFil,
    ).getOrElse {
        return Unit.left()
    }.let {
        JournalførtDokumentForArkiveringHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
            sakId = sakInfo.sakId,
            relaterteHendelser = nonEmptyListOf(relatertHendelse.hendelseId),
            journalpostId = it,
        ).right()
    }

    private fun opprettJournalpostForUtsendelse(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
        versjon: Hendelsesversjon,
        correlationId: CorrelationId,
    ): Either<Unit, JournalførtDokumentForUtsendelseHendelse> = opprettJournalpost(
        sakInfo = sakInfo,
        navn = navn,
        relatertHendelse = relatertHendelse,
        relatertFil = relatertFil,
    ).getOrElse {
        return Unit.left()
    }.let {
        JournalførtDokumentForUtsendelseHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
            sakId = sakInfo.sakId,
            relaterteHendelser = nonEmptyListOf(relatertHendelse.hendelseId),
            journalpostId = it,
        ).right()
    }

    private fun opprettJournalpost(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
    ): Either<Unit, JournalpostId> = dokArkiv.opprettJournalpost(
        dokumentInnhold = JournalpostForSakCommand.Brev(
            fnr = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            dokument = relatertHendelse.dokumentUtenFil.toDokumentMedMetadata(relatertFil.fil),
            sakstype = sakInfo.type,
            navn = navn,
        ),
    ).map {
        it
    }.mapLeft {
        Unit.also { log.error("Feil ved journalføring av LagretDokumentHendelse ${relatertHendelse.hendelseId}", it) }
    }

    private fun hentLagretDokumentHendelseForJournalføring(hendelseId: HendelseId): Either<Unit, Pair<GenerertDokumentHendelse, HendelseFil>> {
        return dokumentHendelseRepo.hentHendelseOgFilFor(hendelseId).let {
            val assertedHendelse = when (it.first is GenerertDokumentHendelse) {
                true -> when (it.first as GenerertDokumentHendelse) {
                    is GenerertDokumentForArkiveringHendelse -> return Unit.left()
                        .also { log.error("Dokument som er lagret for arkivering i SU skal ikke journalføres") }

                    is GenerertDokumentForJournalføringHendelse -> it.first
                    is GenerertDokumentForUtsendelseHendelse -> it.first
                }

                false -> return Unit.left().also {
                    log.error("Dokument hendelse $hendelseId var ikke av typen ${GenerertDokumentHendelse::class.simpleName}")
                }
            }
            val assertedFil = when (it.second) {
                null -> return Unit.left().also { log.error("Fil fantes ikke for å journalføre hendelse $hendelseId") }
                else -> it.second!!
            }
            Pair(assertedHendelse as GenerertDokumentHendelse, assertedFil).right()
        }
    }
}
