package no.nav.su.se.bakover.dokument.application.consumer

import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.hendelse.hendelseFil
import no.nav.su.se.bakover.test.hendelse.lagretDokumentForJournalføringHendelse
import no.nav.su.se.bakover.test.hendelse.lagretDokumentForUtsendelseHendelse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.sakInfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class JournalførDokumentHendelserKonsumentTest {

    @Test
    fun `journalfører dokumenter`() {
        val sakInfoFørsteKall = sakInfo()
        val førsteKall = mapOf(sakInfoFørsteKall.sakId to nonEmptyListOf(HendelseId.generer()))
        val hendelseOgFilFørsteKall = Pair(lagretDokumentForJournalføringHendelse(), hendelseFil())

        val sakInfoAndreKall =
            sakInfo(sakId = UUID.randomUUID(), saksnummer = Saksnummer(2022), fnr = Fnr.generer())
        val andreKall = mapOf(sakInfoAndreKall.sakId to nonEmptyListOf(HendelseId.generer()))
        val hendelseOgFilAndreKall = Pair(lagretDokumentForUtsendelseHendelse(), hendelseFil())

        val konsumentRepo = mock<HendelsekonsumenterRepo> {
            on { hentUteståendeSakOgHendelsesIderForKonsumentOgType(any(), any(), anyOrNull(), anyOrNull()) }
                .thenReturn(førsteKall, andreKall)
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) }.thenReturn(sakInfoFørsteKall.right(), sakInfoAndreKall.right())
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) }
                .thenReturn(Hendelsesversjon(11), Hendelsesversjon(17))
        }
        val dokumentHendelseRepo = mock<DokumentHendelseRepo> {
            on { hentHendelseOgFilFor(any(), anyOrNull()) }.thenReturn(hendelseOgFilFørsteKall, hendelseOgFilAndreKall)
        }
        val personService = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) }.thenReturn(person().right(), person(fnr = Fnr.generer()).right())
        }
        val dokarkiv = mock<DokArkiv> {
            on { opprettJournalpost(any()) }.thenReturn(JournalpostId("J").right(), JournalpostId("Jo").right())
        }

        val service = MockedServices(
            hendelsekonsumenterRepo = konsumentRepo,
            sakService = sakService,
            hendelseRepo = hendelseRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            personService = personService,
            dokArkiv = dokarkiv,
        ).service()

        service.journalførDokumenter(CorrelationId("CorrelationId"))

        verify(konsumentRepo, times(2)).hentUteståendeSakOgHendelsesIderForKonsumentOgType(
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )

        val sakIdCaptor = argumentCaptor<UUID>()
        verify(sakService, times(2)).hentSakInfo(sakIdCaptor.capture())
        sakIdCaptor.firstValue shouldBe førsteKall.keys.first()
        sakIdCaptor.lastValue shouldBe andreKall.keys.first()

        val hendelsesVersjonCaptor = argumentCaptor<UUID>()
        verify(hendelseRepo, times(2)).hentSisteVersjonFraEntitetId(hendelsesVersjonCaptor.capture(), anyOrNull())
        hendelsesVersjonCaptor.firstValue shouldBe førsteKall.keys.first()
        hendelsesVersjonCaptor.lastValue shouldBe andreKall.keys.first()

        val hentHendelseOgFilCaptor = argumentCaptor<HendelseId>()
        verify(dokumentHendelseRepo, times(2)).hentHendelseOgFilFor(hentHendelseOgFilCaptor.capture(), anyOrNull())
        nonEmptyListOf(hentHendelseOgFilCaptor.firstValue) shouldBe førsteKall.values.first()
        nonEmptyListOf(hentHendelseOgFilCaptor.lastValue) shouldBe andreKall.values.first()

        val fnrCaptor = argumentCaptor<Fnr>()
        verify(personService, times(2)).hentPersonMedSystembruker(fnrCaptor.capture())
        fnrCaptor.firstValue shouldBe sakInfoFørsteKall.fnr
        fnrCaptor.lastValue shouldBe sakInfoAndreKall.fnr

        val journalpostCaptor = argumentCaptor<JournalpostForSakCommand.Brev>()
        verify(dokarkiv, times(2)).opprettJournalpost(journalpostCaptor.capture())
        journalpostCaptor.firstValue shouldBe JournalpostForSakCommand.Brev(
            fnr = sakInfoFørsteKall.fnr,
            saksnummer = sakInfoFørsteKall.saksnummer,
            dokument = hendelseOgFilFørsteKall.first.dokumentUtenFil.toDokumentMedMetadata(hendelseOgFilFørsteKall.second.fil),
            sakstype = sakInfoFørsteKall.type,
            navn = person().navn,
        )
        journalpostCaptor.secondValue shouldBe JournalpostForSakCommand.Brev(
            fnr = sakInfoAndreKall.fnr,
            saksnummer = sakInfoAndreKall.saksnummer,
            dokument = hendelseOgFilAndreKall.first.dokumentUtenFil.toDokumentMedMetadata(hendelseOgFilAndreKall.second.fil),
            sakstype = sakInfoAndreKall.type,
            navn = person().navn,
        )

        val journalpostDokumentHendelse = argumentCaptor<JournalførtDokumentHendelse>()
        verify(dokumentHendelseRepo, times(2)).lagre(journalpostDokumentHendelse.capture(), anyOrNull<SessionContext>())
        journalpostDokumentHendelse.firstValue shouldBe JournalførtDokumentForArkiveringHendelse(
            hendelseId = journalpostDokumentHendelse.firstValue.hendelseId,
            hendelsestidspunkt = fixedTidspunkt,
            versjon = Hendelsesversjon(value = 12),
            meta = DefaultHendelseMetadata.fraCorrelationId(CorrelationId("CorrelationId")),
            sakId = førsteKall.keys.first(),
            relaterteHendelser = journalpostDokumentHendelse.firstValue.relaterteHendelser,
            journalpostId = JournalpostId(value = "J"),
        )
        journalpostDokumentHendelse.secondValue shouldBe JournalførtDokumentForUtsendelseHendelse(
            hendelseId = journalpostDokumentHendelse.secondValue.hendelseId,
            hendelsestidspunkt = fixedTidspunkt,
            versjon = Hendelsesversjon(value = 18),
            meta = DefaultHendelseMetadata.fraCorrelationId(CorrelationId("CorrelationId")),
            sakId = andreKall.keys.first(),
            relaterteHendelser = journalpostDokumentHendelse.secondValue.relaterteHendelser,
            journalpostId = JournalpostId(value = "Jo"),
        )

        val hendelseIdForKonsumentCaptor = argumentCaptor<HendelseId>()
        verify(konsumentRepo, times(2)).lagre(
            hendelseIdForKonsumentCaptor.capture(),
            anyOrNull(),
            anyOrNull(),
        )
        hendelseIdForKonsumentCaptor.firstValue shouldBe journalpostDokumentHendelse.firstValue.hendelseId
        hendelseIdForKonsumentCaptor.secondValue shouldBe journalpostDokumentHendelse.secondValue.hendelseId
    }

    data class MockedServices(
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val dokArkiv: DokArkiv = mock(),
        val dokumentHendelseRepo: DokumentHendelseRepo = mock(),
        val hendelsekonsumenterRepo: HendelsekonsumenterRepo = mock(),
        val hendelseRepo: HendelseRepo = mock(),
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
        val clock: Clock = fixedClock,
    ) {
        fun service(): JournalførDokumentHendelserKonsument {
            return JournalførDokumentHendelserKonsument(
                sakService = sakService,
                personService = personService,
                dokArkiv = dokArkiv,
                dokumentHendelseRepo = dokumentHendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                hendelseRepo = hendelseRepo,
                sessionFactory = sessionFactory,
                clock = clock,
            )
        }

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                sakService,
                personService,
                dokArkiv,
                dokumentHendelseRepo,
                hendelsekonsumenterRepo,
                hendelseRepo,
            )
        }
    }
}
