package no.nav.su.se.bakover.domain.mottaker

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.DokumentFormaal
import dokument.domain.DokumentRepo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import org.mockito.kotlin.eq
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
        sakId: UUID,
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
                actual.sakId == sakId &&
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
    fun `hentMottaker returnerer Right null nar mottaker ikke finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mident = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
        )
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mident) } doReturn null
        }
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.hentMottaker(mident, sakId).shouldBeRight(null)

        verify(mottakerRepo).hentMottaker(mident)
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )
        val mident = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
        )
        val mottakerSomDomain = mottaker.toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )
        val mident = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
        )
        val mottakerSomDomain = mottaker.toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
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
    fun `Kan ikke hente mottaker med brevtype annet`() {
        val sakId = UUID.randomUUID()
        val mident = MottakerIdentifikator(
            referanseId = UUID.randomUUID(),
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.ANNET,
        )

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        val feil = service.hentMottaker(mident, sakId).shouldBeLeft()
        (feil as FeilkoderMottaker.UgyldigMottakerRequest).feil shouldContain "Tillatte verdier"

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker med ugyldig brevtype`() {
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = "UGYLDIG_BREVTYPE",
        )

        val feil = service.lagreMottaker(mottaker, sakId).shouldBeLeft()
        (feil as FeilkoderMottaker.UgyldigMottakerRequest).feil shouldContain "Ugyldig brevtype"

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker med brevtype annet`() {
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.ANNET.name,
        )

        val feil = service.lagreMottaker(mottaker, sakId).shouldBeLeft()
        (feil as FeilkoderMottaker.UgyldigMottakerRequest).feil shouldContain "Tillatte verdier"

        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke oppdatere mottaker med ugyldig id`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)
        val mottaker = OppdaterMottaker(
            id = "ikke-gyldig-uuid",
            navn = "Tester",
            foedselsnummer = "01010112345",
            adresse = DistribueringsadresseRequest(
                adresselinje1 = "Gate 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        val feil = service.oppdaterMottaker(mottaker, sakId).shouldBeLeft()
        (feil as FeilkoderMottaker.UgyldigMottakerRequest).feil shouldContain "MottakerId er ikke en gyldig UUID"

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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker, sakId)))
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker, sakId)))

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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            id = UUID.randomUUID().toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()
        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker, sakId)))

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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            id = UUID.randomUUID().toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
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
                    domain.sakId == sakId &&
                    domain.referanseId.toString() == oppdaterMottaker.referanseId &&
                    domain.referanseType.name == oppdaterMottaker.referanseType
            },
        )
        verify(vedtakRepo, times(2)).finnesVedtakForRevurderingId(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke lagre mottaker for soknad nar vedtak finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()

        val mottakerRepo = mock<MottakerRepoImpl>()
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = mock<VedtakRepo> {
            on { finnesVedtakForSøknadsbehandlingId(any()) } doReturn true
            on { finnesVedtakForRevurderingId(any()) } doReturn false
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.SØKNAD.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(vedtakRepo, times(1)).finnesVedtakForSøknadsbehandlingId(any())
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

/*    @Test
    fun `Kan ikke lagre mottaker for klage nar dokument finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerRepo = mock<MottakerRepoImpl>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentForKlage(referanseId) } doReturn listOf(
                dokumentUtenMetadataInformasjonViktig().leggTilMetadata(
                    metadata = Dokument.Metadata(sakId = sakId),
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.KLAGE.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(dokumentRepo).hentForKlage(referanseId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }*/

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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        )

        service.lagreMottaker(
            mottaker = mottaker,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo, times(1))
            .lagreMottaker(argThat(matcherMottaker(mottaker, sakId)))
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
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
    fun `slettMottaker returnerer Right Unit nar mottaker ikke finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
        )
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mottakerIdentifikator) } doReturn null
        }
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeRight()

        verify(mottakerRepo).hentMottaker(mottakerIdentifikator)
        verify(mottakerRepo, times(0)).slettMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke slette mottaker ved mismatch pa sakId`() {
        val sakIdIPath = UUID.randomUUID()
        val sakIdIMottaker = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        ).toDomain(sakIdIMottaker).getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mottakerIdentifikator) } doReturn mottaker
        }
        val dokumentRepo = mock<DokumentRepo>()
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakIdIPath,
        ).shouldBeLeft().let { it shouldBe FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker }

        verify(mottakerRepo).hentMottaker(mottakerIdentifikator)
        verify(mottakerRepo, times(0)).slettMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke slette mottaker hvis vedtaksbrev finnes for revurdering allerede`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.VEDTAK,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        ).toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
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
            brevtype = DokumentFormaal.VEDTAK,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.VEDTAK.name,
        ).toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
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
    fun `Kan slette revurdering forhandsvarselmottaker nar kun vedtaksbrev finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = DokumentFormaal.FORHANDSVARSEL,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.FORHANDSVARSEL.name,
        ).toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
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
        ).shouldBeRight()

        verify(dokumentRepo).hentForRevurdering(referanseId)
        verify(mottakerRepo).hentMottaker(eq(mottakerIdentifikator), anyOrNull())
        verify(mottakerRepo).slettMottaker(mottaker.id)
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
                ).copy(
                    dokumentFormaal = DokumentFormaal.FORHANDSVARSEL,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.FORHANDSVARSEL.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(dokumentRepo).hentForRevurdering(referanseId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan lagre mottaker for revurdering forhandsvarsel nar informasjon viktig er annet formaal`() {
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
                ).copy(
                    dokumentFormaal = DokumentFormaal.ANNET,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.toString(),
            brevtype = DokumentFormaal.FORHANDSVARSEL.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeRight()

        verify(dokumentRepo).hentForRevurdering(referanseId)
        verify(mottakerRepo, times(1)).lagreMottaker(argThat(matcherMottaker(mottaker, sakId)))
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    /*@Test
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.TILBAKEKREVING.toString(),
            brevtype = DokumentFormaal.FORHANDSVARSEL.name,
        )

        service.lagreMottaker(mottaker, sakId).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.KanIkkeLagreMottaker
        }

        verify(dokumentRepo).hentForSak(sakId)
        verify(mottakerRepo, times(0)).lagreMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

    @Test
    fun `Kan ikke slette tilbakekreving forhandsvarselmottaker nar forhandsvarselbrev finnes`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerIdentifikator = MottakerIdentifikator(
            referanseId = referanseId,
            referanseType = ReferanseTypeMottaker.TILBAKEKREVING,
            brevtype = DokumentFormaal.FORHANDSVARSEL,
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
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.TILBAKEKREVING.toString(),
            brevtype = DokumentFormaal.FORHANDSVARSEL.name,
        ).toDomain(sakId).getOrElse { throw IllegalStateException("Skal ikke feile") }
        val mottakerRepo = mock<MottakerRepoImpl> {
            on { hentMottaker(mottakerIdentifikator) } doReturn mottaker
        }
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
        val vedtakRepo = vedtakRepoSomIkkeHarVedtak()
        val service = MottakerServiceImpl(mottakerRepo, dokumentRepo, vedtakRepo)

        service.slettMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
        ).shouldBeLeft().let {
            it shouldBe FeilkoderMottaker.BrevFinnesIDokumentBasen
        }

        verify(dokumentRepo).hentForSak(sakId)
        verify(mottakerRepo).hentMottaker(eq(mottakerIdentifikator), anyOrNull())
        verify(mottakerRepo, times(0)).slettMottaker(any())
        verifyNoMoreInteractions(dokumentRepo, mottakerRepo, vedtakRepo)
    }

     */
}
