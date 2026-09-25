package no.nav.su.se.bakover.service.historisk.revurdering

import arrow.core.Either
import arrow.core.right
import dokument.domain.brev.BrevService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonOversikt
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBehandlingstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjegrunnlag
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløpForVedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløpsperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOppdragLinjeId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSaksreferanse
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsavgrensning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.SlettHistoriskAlderProjeksjonResultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import satser.domain.SatsFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class HistoriskInfotrygdRevurderingServiceTest {
    @Test
    fun `oppretter behandling med fastlåste vedtaksreferanser`() {
        val projeksjonRepo = HistoriskAlderProjeksjonRepoFake(
            projeksjonId = projeksjonId,
            grunnlag = listOf(grunnlag()),
        )
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(projeksjonRepo, revurderingRepo = revurderingRepo)

        val opprettet = service.opprett(command()).shouldBeRight()

        opprettet.projeksjonId shouldBe projeksjonId
        opprettet.periode shouldBe periode
        opprettet.vedtakSomRevurderesMånedsvis.keys.toList() shouldBe periode.måneder()
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `avviser delmåneder før sak eller projeksjon slås opp`() {
        val sakRepo = mock<SakRepo>()
        val projeksjonRepo = mock<HistoriskAlderProjeksjonRepo>()
        val service = service(projeksjonRepo, sakRepo = sakRepo)

        service.opprett(
            command(
                periode = Periode.create(
                    LocalDate.of(2020, 1, 2),
                    LocalDate.of(2020, 2, 29),
                ),
            ),
        ).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.PeriodenMåBeståAvHeleMåneder

        verify(sakRepo, never()).hentSakInfoForIdent(any(), any(), any())
        verify(projeksjonRepo, never()).hentSisteFullførteProjeksjonIdForPerson(any())
    }

    @Test
    fun `avviser periode som overlapper første innvilgede SU-app-måned`() {
        val service = service(
            projeksjonRepo = HistoriskAlderProjeksjonRepoFake(projeksjonId, listOf(grunnlag())),
            førsteInnvilgedeSuAppMåned = FørsteInnvilgedeSuAppMåned { februar(2020) },
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .OverlapperInnvilgetSuAppYtelse(februar(2020))
    }

    @Test
    fun `avviser første måned uten historisk vedtak`() {
        val service = service(
            projeksjonRepo = HistoriskAlderProjeksjonRepoFake(
                projeksjonId = projeksjonId,
                grunnlag = listOf(grunnlag(tilOgMed = januar(2020).tilOgMed)),
            ),
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .MånedManglerHistoriskVedtak(februar(2020))
    }

    @Test
    fun `avslutter behandling med optimistisk versjonering`() {
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = HistoriskAlderProjeksjonRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
        )
        val opprettet = service.opprett(command()).shouldBeRight()

        val avsluttet = service.avslutt(
            id = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("S654321"),
            begrunnelse = "Behandlingen skal ikke gjennomføres.",
        ).shouldBeRight()

        avsluttet.status shouldBe HistoriskInfotrygdRevurderingStatus.AVSLUTTET
        avsluttet.versjon shouldBe 1
        revurderingRepo.hent(opprettet.id) shouldBe avsluttet
    }

    @Test
    fun `returnerer versjonskonflikt når behandlingen er endret etter uthenting`() {
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake(lagreResultat = false)
        val service = service(
            projeksjonRepo = HistoriskAlderProjeksjonRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
        )
        val opprettet = service.opprett(command()).shouldBeRight()

        service.avslutt(
            id = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("S654321"),
            begrunnelse = "Behandlingen skal ikke gjennomføres.",
        ).shouldBeLeft() shouldBe KunneIkkeEndreHistoriskInfotrygdRevurdering.Versjonskonflikt

        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    private fun service(
        projeksjonRepo: HistoriskAlderProjeksjonRepo,
        revurderingRepo: HistoriskInfotrygdRevurderingRepo = HistoriskInfotrygdRevurderingRepoFake(),
        sakRepo: SakRepo = sakRepo(),
        førsteInnvilgedeSuAppMåned: FørsteInnvilgedeSuAppMåned = FørsteInnvilgedeSuAppMåned { null },
    ) = HistoriskInfotrygdRevurderingService(
        sakRepo = sakRepo,
        historiskAlderProjeksjonRepo = projeksjonRepo,
        revurderingRepo = revurderingRepo,
        førsteInnvilgedeSuAppMåned = førsteInnvilgedeSuAppMåned,
        brevService = mock<BrevService>(),
        satsFactory = mock<SatsFactory>(),
        clock = clock,
    )

    private fun sakRepo(): SakRepo = mock {
        on { hentSakInfoForIdent(fnr, Sakstype.ALDER, null) } doReturn sakInfo
    }

    private fun command(periode: Periode = HistoriskInfotrygdRevurderingServiceTest.periode) =
        OpprettHistoriskInfotrygdRevurderingCommand(
            fnr = fnr,
            periode = periode,
            saksbehandler = NavIdentBruker.Saksbehandler("S123456"),
        )

    private class HistoriskInfotrygdRevurderingRepoFake(
        private val lagreResultat: Boolean = true,
    ) : HistoriskInfotrygdRevurderingRepo {
        private val behandlinger = mutableMapOf<HistoriskInfotrygdRevurderingId, HistoriskInfotrygdRevurdering>()
        private val vedtak = mutableMapOf<UUID30, HistoriskInfotrygdRevurderingsvedtak>()
        private val transactionContext = mock<TransactionContext>()

        override fun opprett(
            revurdering: HistoriskInfotrygdRevurdering,
            transactionContext: TransactionContext,
        ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
            behandlinger[revurdering.id] = revurdering
            return revurdering.right()
        }

        override fun lagre(
            revurdering: HistoriskInfotrygdRevurdering,
            forventetVersjon: Long,
            transactionContext: TransactionContext,
        ): Boolean {
            if (!lagreResultat || behandlinger[revurdering.id]?.versjon != forventetVersjon) return false
            behandlinger[revurdering.id] = revurdering
            return true
        }

        override fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering? = behandlinger[id]

        override fun lagreVedtak(
            vedtak: HistoriskInfotrygdRevurderingsvedtak,
            transactionContext: TransactionContext,
        ) {
            this.vedtak[vedtak.utbetalingId] = vedtak
        }

        override fun hentVedtakForUtbetaling(
            utbetalingId: UUID30,
            sessionContext: SessionContext?,
        ): HistoriskInfotrygdRevurderingsvedtak? = vedtak[utbetalingId]

        override fun defaultTransactionContext(): TransactionContext = transactionContext
    }

    private class HistoriskAlderProjeksjonRepoFake(
        private val projeksjonId: UUID,
        private val grunnlag: List<HistoriskInfotrygdTidslinjegrunnlag>,
    ) : HistoriskAlderProjeksjonRepo {
        override fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID = projeksjonId

        override fun hentOriginalTidslinjegrunnlag(
            projeksjonId: UUID,
            personident: String,
            periode: Periode,
        ): List<HistoriskInfotrygdTidslinjegrunnlag> = grunnlag

        override fun startProjeksjon(importId: UUID, dryRun: Boolean, maksAntallStønader: Int?): UUID =
            error("Ikke brukt")

        override fun lagreBatch(projeksjonId: UUID, importId: UUID, stønader: List<HistoriskAldersstønad>) =
            error("Ikke brukt")

        override fun fullførProjeksjon(
            projeksjonId: UUID,
            antallStønader: Int,
            avviksoppsummering: Map<String, Int>,
            forbehold: Set<String>,
        ) = error("Ikke brukt")

        override fun markerFeilet(projeksjonId: UUID, beskrivelse: String) = error("Ikke brukt")
        override fun hentProjeksjoner(importId: UUID): List<HistoriskAlderProjeksjonOversikt> = error("Ikke brukt")
        override fun slettProjeksjon(importId: UUID, projeksjonId: UUID): SlettHistoriskAlderProjeksjonResultat =
            error("Ikke brukt")

        override fun harSak(personident: String): Boolean = error("Ikke brukt")
        override fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode> = error("Ikke brukt")
        override fun hentMånedsbeløpForVedtak(vedtakId: HistoriskVedtakId): HistoriskMånedsbeløpForVedtak =
            error("Ikke brukt")
    }

    private companion object {
        val fnr: Fnr = Fnr.generer()
        val sakId: UUID = UUID.randomUUID()
        val projeksjonId: UUID = UUID.randomUUID()
        val periode: Periode = Periode.create(januar(2020).fraOgMed, februar(2020).tilOgMed)
        val sakInfo = SakInfo(sakId, Saksnummer(2021L), fnr, Sakstype.ALDER)
        val clock: Clock = Clock.fixed(Instant.parse("2020-03-01T10:00:00Z"), ZoneOffset.UTC)

        fun grunnlag(
            tilOgMed: LocalDate = februar(2020).tilOgMed,
        ) = HistoriskInfotrygdTidslinjegrunnlag(
            vedtak = HistoriskVedtaksperiode(
                stønadId = HistoriskStønadId(1),
                vedtakId = HistoriskVedtakId(2),
                oppdragId = "oppdrag-1",
                opphørskodeRaw = null,
                opphørsgrunn = null,
                fraOgMed = januar(2020).fraOgMed,
                tilOgMed = tilOgMed,
                behandlingstypeRaw = "S",
                behandlingstype = HistoriskBehandlingstype.SØKNAD,
                resultatRaw = "I",
                resultat = HistoriskResultat.INNVILGET,
                bosituasjonRaw = "EN",
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                årligYtelsesbeløp = BigDecimal(120_000),
                revurderingsdato = null,
                registrertTidspunkt = "2020-01-01T10:00:00",
                endringskoder = emptyList(),
                saksreferanse = HistoriskSaksreferanse(null, null, null, null),
                sendtTilOs = null,
                mottattFraOs = null,
                godkjentAvOs = null,
            ),
            stønadsavgrensning = HistoriskStønadsavgrensning(
                stønadId = HistoriskStønadId(1),
                fraOgMed = januar(2020).fraOgMed,
                tilOgMed = tilOgMed,
            ),
            månedsbeløp = listOf(
                HistoriskMånedsbeløpsperiode(
                    linjeId = HistoriskOppdragLinjeId("1"),
                    fraOgMed = januar(2020).fraOgMed,
                    tilOgMed = tilOgMed,
                    sats = BigDecimal(10_000),
                    fradrag = BigDecimal(1_000),
                    fradragskoder = listOf("ARBM"),
                ),
            ),
        )
    }
}
