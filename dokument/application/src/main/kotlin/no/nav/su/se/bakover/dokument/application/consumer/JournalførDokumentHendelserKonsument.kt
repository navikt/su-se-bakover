package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.Nel
import arrow.core.Tuple4
import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokumentForArkiveringHendelse
import dokument.domain.hendelser.GenerertDokumentForJournalføringHendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelseHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import dokument.domain.hendelser.LagretDokumentForJournalføring
import dokument.domain.hendelser.LagretDokumentForUtsendelse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.CorrelationId
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
            hendelsestype = LagretDokumentForJournalføring,
        ).forEach { (sakId, hendelsesIder) ->
            prosesserSakForArkiveringAvJournalførtDokument(
                sakId = sakId,
                hendelsesIder = hendelsesIder,
                correlationId = correlationId,
            )
        }

        hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = LagretDokumentForUtsendelse,
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
        val sakInfo = sakService.hentSakInfo(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke hente sakInfo $sakId for å journalføre lagret dokument for arkivering. hendelsesIder $hendelsesIder") }

        hendelsesIder.map { relatertHendelsesId ->
            val (nesteVersjon, relatertHendelse, relatertFil, navn) = hentDataForJournalføringAvDokument(
                sakInfo,
                relatertHendelsesId,
            )

            opprettJournalpostForArkivering(
                sakInfo = sakInfo,
                navn = navn,
                relatertHendelse = relatertHendelse,
                relatertFil = relatertFil,
                versjon = nesteVersjon,
                correlationId = correlationId,
            ).let { forArkivering ->
                sessionFactory.withSessionContext {
                    dokumentHendelseRepo.lagre(forArkivering, it)
                    hendelsekonsumenterRepo.lagre(forArkivering.hendelseId, konsumentId, it)
                }
            }
        }
    }

    private fun prosesserSakForUtsendelseAvJournalførtDokument(
        sakId: UUID,
        hendelsesIder: Nel<HendelseId>,
        correlationId: CorrelationId,
    ) {
        val sakInfo = sakService.hentSakInfo(sakId)
            .getOrElse { throw IllegalStateException("Kunne ikke hente sakInfo $sakId for å journalføre lagret dokument for utsendelse") }

        hendelsesIder.map { relatertHendelsesId ->
            val (nesteVersjon, relatertHendelse, relatertFil, navn) = hentDataForJournalføringAvDokument(
                sakInfo,
                relatertHendelsesId,
            )
            opprettJournalpostForUtsendelse(
                sakInfo = sakInfo,
                navn = navn,
                relatertHendelse = relatertHendelse,
                relatertFil = relatertFil,
                versjon = nesteVersjon,
                correlationId = correlationId,
            ).let { forUtsendelse ->
                sessionFactory.withSessionContext {
                    dokumentHendelseRepo.lagre(forUtsendelse, it)
                    hendelsekonsumenterRepo.lagre(forUtsendelse.hendelseId, konsumentId, it)
                }
            }
        }
    }

    private fun hentDataForJournalføringAvDokument(
        sakInfo: SakInfo,
        relatertHendelsesId: HendelseId,
    ): Tuple4<Hendelsesversjon, GenerertDokumentHendelse, HendelseFil, Person.Navn> {
        val nesteVersjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakInfo.sakId)?.inc()
            ?: throw IllegalStateException("Kunne ikke hente siste versjon for sak ${sakInfo.sakId} for å journalføre dokument")

        val (relatertHendelse, relatertFil) = hentLagretDokumentHendelseForJournalføring(relatertHendelsesId)

        val person = personService.hentPersonMedSystembruker(sakInfo.fnr).getOrElse {
            throw IllegalStateException("Feil ved henting av person for journalføring av dokument for hendelse ${relatertHendelse.hendelseId} for sak ${sakInfo.sakId}")
        }

        return Tuple4(nesteVersjon, relatertHendelse, relatertFil, person.navn)
    }

    private fun opprettJournalpostForArkivering(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
        versjon: Hendelsesversjon,
        correlationId: CorrelationId,
    ): JournalførtDokumentForArkiveringHendelse = opprettJournalpost(
        sakInfo = sakInfo,
        navn = navn,
        relatertHendelse = relatertHendelse,
        relatertFil = relatertFil,
    ).let {
        JournalførtDokumentForArkiveringHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
            sakId = sakInfo.sakId,
            relaterteHendelser = nonEmptyListOf(relatertHendelse.hendelseId),
            journalpostId = it,
        )
    }

    private fun opprettJournalpostForUtsendelse(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
        versjon: Hendelsesversjon,
        correlationId: CorrelationId,
    ): JournalførtDokumentForUtsendelseHendelse = opprettJournalpost(
        sakInfo = sakInfo,
        navn = navn,
        relatertHendelse = relatertHendelse,
        relatertFil = relatertFil,
    ).let {
        JournalførtDokumentForUtsendelseHendelse(
            hendelseId = HendelseId.generer(),
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
            sakId = sakInfo.sakId,
            relaterteHendelser = nonEmptyListOf(relatertHendelse.hendelseId),
            journalpostId = it,
        )
    }

    private fun opprettJournalpost(
        sakInfo: SakInfo,
        navn: Person.Navn,
        relatertHendelse: GenerertDokumentHendelse,
        relatertFil: HendelseFil,
    ): JournalpostId = dokArkiv.opprettJournalpost(
        dokumentInnhold = JournalpostForSakCommand.Brev(
            fnr = sakInfo.fnr,
            saksnummer = sakInfo.saksnummer,
            dokument = relatertHendelse.dokumentUtenFil.toDokumentMedMetadata(relatertFil.fil),
            sakstype = sakInfo.type,
            navn = navn,
        ),
    ).getOrElse {
        throw IllegalStateException("Feil ved journalføring av LagretDokumentHendelse ${relatertHendelse.hendelseId}")
    }

    private fun hentLagretDokumentHendelseForJournalføring(hendelseId: HendelseId): Pair<GenerertDokumentHendelse, HendelseFil> {
        return dokumentHendelseRepo.hentHendelseOgFilFor(hendelseId).let {
            val assertedHendelse = when (it.first is GenerertDokumentHendelse) {
                true -> when (it.first as GenerertDokumentHendelse) {
                    is GenerertDokumentForArkiveringHendelse -> throw IllegalStateException("Dokument som er lagret for arkivering i SU skal ikke journalføres")
                    is GenerertDokumentForJournalføringHendelse -> it.first
                    is GenerertDokumentForUtsendelseHendelse -> it.first
                }

                false -> throw IllegalStateException("")
            }
            val assertedFil = when (it.second) {
                null -> throw IllegalStateException("Fil fantes ikke for å journalføre hendelse $hendelseId")
                else -> it.second!!
            }
            Pair(assertedHendelse as GenerertDokumentHendelse, assertedFil)
        }
    }
}
