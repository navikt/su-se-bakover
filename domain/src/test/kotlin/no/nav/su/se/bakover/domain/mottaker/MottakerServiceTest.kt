package no.nav.su.se.bakover.domain.mottaker

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.mottaker.MottakerRepoImpl
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

internal class MottakerServiceTest {

    // Denne ignorerer bevisst uuiden
    fun matcherMottaker(
        expected: LagreMottaker,
    ): ArgumentMatcher<MottakerDomain> =
        ArgumentMatcher { actual ->
            actual.navn == expected.navn &&
                actual.foedselsnummer.toString() == expected.foedselsnummer &&
                actual.adresse == expected.adresse &&
                actual.sakId.toString() == expected.sakId &&
                actual.referanseId.toString() == expected.referanseId &&
                actual.referanseType.name == expected.referanseType
        }

    @Test
    fun `Kan hente mottaker for revurdering`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )
        val mident = MottakerIdentifikator(referanseId = referanseId, referanseType = ReferanseTypeMottaker.REVURDERING)
        val mottakerSomDomain = mottaker.toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mident) } doReturn mottakerSomDomain
        }
        val dokumentRepo = mock<DokumentRepo>()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val hentetMottaker = service.hentMottaker(mident, sakId = sakId)
        hentetMottaker.getOrElse { throw IllegalStateException("sakidmatchetikke, skal ikke skje her ") } shouldBe mottakerSomDomain
        verify(mottakerRepo, times(1)).hentMottaker(mident)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan ikke hente mottaker for revurdering ved mismatch på sakid`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )
        val mident = MottakerIdentifikator(referanseId = referanseId, referanseType = ReferanseTypeMottaker.REVURDERING)
        val mottakerSomDomain = mottaker.toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mident) } doReturn mottakerSomDomain
        }
        val dokumentRepo = mock<DokumentRepo>()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        service.hentMottaker(mident, sakId = UUID.randomUUID()).shouldBeLeft().let { it shouldBe FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker }
        verify(mottakerRepo, times(1)).hentMottaker(mident)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn emptyList()
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker)))
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering men ikke oppdaterer da brevet er sendt aka dokument finnes i dokubase`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn emptyList()
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker)))

        val vedtaksdokumentForRevurdering = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = """{"some":"json"}""",
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = referanseId),
            distribueringsadresse = null,
        )

        whenever(dokumentRepo.hentForRevurdering(referanseId))
            .thenReturn(listOf(vedtaksdokumentForRevurdering))

        val oppdaterMottaker = OppdaterMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            id = UUID.randomUUID().toString(),
        )

        service.oppdaterMottaker(
            oppdaterMottaker.copy(navn = "Nytt navn"),
            sakId,
        ).shouldBeLeft()
            .let { it shouldBe FeilkoderMottaker.KanIkkeOppdatereMottaker }
        verify(dokumentRepo, times(2)).hentForRevurdering(referanseId)

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering og oppdaterer da brevet ikke er sendt aka dokument ikke finnes i dokubase`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn emptyList()
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()
        val mottakerDomain = mottaker.toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") }
        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker)))

        val oppdaterMottaker = OppdaterMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            id = UUID.randomUUID().toString(),
        )
        val nyttnavnForOppdatering = "Nytt navn"
        val skalMatche = oppdaterMottaker.copy(navn = nyttnavnForOppdatering)
        service.oppdaterMottaker(
            skalMatche,
            sakId,
        ).shouldBeRight()
        verify(mottakerRepo, times(1)).oppdaterMottaker(skalMatche.toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") })
        verify(dokumentRepo, times(2)).hentForRevurdering(referanseId)

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering selvom brev av annen type enn vedtak finnes på revurderingen`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()

        val vedtaksdokumentForRevurdering = Dokument.MedMetadata.Informasjon.Viktig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = """{"some":"json"}""",
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = referanseId),
            distribueringsadresse = null,
        )

        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn listOf(vedtaksdokumentForRevurdering)
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker)))
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker hvis vedtaksbrev finnes for revurdering allerede `() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerRepo = mock<MottakerRepoImpl>()

        val vedtaksdokumentForRevurdering = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = """{"some":"json"}""",
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = referanseId),
            distribueringsadresse = null,
        )

        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn listOf(vedtaksdokumentForRevurdering)
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        )
        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeLeft()

        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan ikke slette mottaker hvis vedtaksbrev finnes for revurdering allerede`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
        )

        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        ).toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mottakerIdentifikator) } doReturn mottaker
        }
        val vedtaksdokumentForRevurdering = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = """{"some":"json"}""",
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = referanseId),
            distribueringsadresse = null,
        )

        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn listOf(vedtaksdokumentForRevurdering)
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeLeft()
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)

        verify(mottakerRepo, times(1)).hentMottaker(any())

        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verify(mottakerRepo, times(0)).slettMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }

    @Test
    fun `Kan  slette mottaker hvis annet brev finnes for revurdering allerede`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
        )

        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = Distribueringsadresse(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
        ).toDomain().getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mottakerIdentifikator) } doReturn mottaker
        }
        val vedtaksdokumentForRevurdering = Dokument.MedMetadata.Informasjon.Annet(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = """{"some":"json"}""",
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = referanseId),
            distribueringsadresse = null,
        )

        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn listOf(vedtaksdokumentForRevurdering)
        }
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeRight()
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)

        verify(mottakerRepo, times(1)).hentMottaker(any())

        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verify(mottakerRepo, times(1)).slettMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo)
    }
}
