package no.nav.su.se.bakover.domain.mottaker

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.mottaker.MottakerRepoImpl
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
            val identMatcher = when (actual) {
                is MottakerFnrDomain ->
                    actual.foedselsnummer.toString() == expected.foedselsnummer
                is MottakerOrgnummerDomain ->
                    actual.orgnummer == expected.orgnummer
            }

            identMatcher &&
                actual.navn == expected.navn &&
                actual.adresse == expected.adresse.toDomain() &&
                actual.sakId.toString() == expected.sakId &&
                actual.referanseId.toString() == expected.referanseId &&
                actual.referanseType.name == expected.referanseType &&
                actual.brevtype.name == expected.brevtype
        }

    private fun vedtakRepoSomIkkeHarVedtak(): VedtakRepo =
        mock {
            on { finnesVedtakForRevurderingId(any()) } doReturn false
            on { finnesVedtakForSøknadsbehandlingId(any()) } doReturn false
        }

    @Test
    fun `Kan hente mottaker for revurdering`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val hentetMottaker = service.hentMottaker(mident, sakId = sakId)
        hentetMottaker.getOrElse { throw IllegalStateException("sakidmatchetikke, skal ikke skje her ") } shouldBe mottakerSomDomain
        verify(mottakerRepo, times(1)).hentMottaker(mident)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke hente mottaker for revurdering ved mismatch på sakid`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        service.hentMottaker(mident, sakId = UUID.randomUUID()).shouldBeLeft().let { it shouldBe FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker }
        verify(mottakerRepo, times(1)).hentMottaker(mident)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        verify(vedtakRepo, times(1)).finnesVedtakForRevurderingId(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering men ikke oppdaterer da brevet er sendt aka dokument finnes i dokubase`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        whenever(vedtakRepo.finnesVedtakForRevurderingId(any())).thenReturn(false, true)
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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

        val oppdaterMottaker = OppdaterMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        verify(vedtakRepo, times(2)).finnesVedtakForRevurderingId(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering og oppdaterer da brevet ikke er sendt aka dokument ikke finnes i dokubase`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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

        val oppdaterMottaker = OppdaterMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        verify(mottakerRepo, times(1)).oppdaterMottaker(
            argThat { domain ->
                domain is MottakerFnrDomain &&
                    domain.navn == nyttnavnForOppdatering &&
                    domain.foedselsnummer.toString() == oppdaterMottaker.foedselsnummer &&
                    domain.adresse == oppdaterMottaker.adresse.toDomain() &&
                    domain.sakId.toString() == oppdaterMottaker.sakId &&
                    domain.referanseId.toString() == oppdaterMottaker.referanseId &&
                    domain.referanseType.name == oppdaterMottaker.referanseType
            },
        )
        verify(vedtakRepo, times(2)).finnesVedtakForRevurderingId(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering selvom brev av annen type enn vedtak finnes på revurderingen`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()

        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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
        verify(vedtakRepo, times(1)).finnesVedtakForRevurderingId(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker hvis vedtaksbrev finnes for revurdering allerede `() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerRepo = mock<MottakerRepoImpl>()

        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        whenever(vedtakRepo.finnesVedtakForRevurderingId(any())).doReturn(true)
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
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

        verify(vedtakRepo, times(1)).finnesVedtakForRevurderingId(any())
        verify(mottakerRepo, times(0)).lagreMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
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
            adresse = DistribueringsadresseRequest(
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
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeLeft()
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)

        verify(mottakerRepo, times(1)).hentMottaker(any(), anyOrNull())

        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verify(mottakerRepo, times(0)).slettMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
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
            adresse = DistribueringsadresseRequest(
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
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeRight()
        verify(dokumentRepo, times(1)).hentForRevurdering(referanseId)

        verify(mottakerRepo, times(1)).hentMottaker(any(), anyOrNull())

        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verify(mottakerRepo, times(1)).slettMottaker(any())

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker for revurdering forhandsvarsel nar forhandsvarselbrev allerede finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerRepo = mock<MottakerRepoImpl>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForRevurdering(referanseId) } doReturn listOf(
                dokumentUtenMetadataInformasjonViktig().leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = sakId,
                        revurderingId = referanseId,
                    ),
                    distribueringsadresse = null,
                ),
            )
        }

        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = BrevtypeMottaker.FORHANDSVARSEL.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(dokumentRepo).hentForRevurdering(referanseId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker for tilbakekreving forhandsvarsel nar forhandsvarselbrev allerede finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerRepo = mock<MottakerRepoImpl>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForSak(sakId) } doReturn listOf(
                dokumentUtenMetadataInformasjonViktig().leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = sakId,
                        tilbakekrevingsbehandlingId = referanseId,
                    ),
                    distribueringsadresse = null,
                ),
            )
        }

        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = LagreMottaker(
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            sakId = sakId.toString(),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.TILBAKEKREVING.toString(),
            brevtype = BrevtypeMottaker.FORHANDSVARSEL.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(dokumentRepo).hentForSak(sakId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }
}
