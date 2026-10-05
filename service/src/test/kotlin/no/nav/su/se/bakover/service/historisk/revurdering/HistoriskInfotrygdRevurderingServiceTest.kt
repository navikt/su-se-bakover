package no.nav.su.se.bakover.service.historisk.revurdering

import arrow.core.Either
import arrow.core.right
import dokument.domain.brev.BrevService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdBeløpsperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjeRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjegrunnlag
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjevedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsavgrensning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class HistoriskInfotrygdRevurderingServiceTest {
    @Test
    fun `oppretter behandling med fastlåste vedtaksreferanser`() {
        val projeksjonRepo = TidslinjeRepoFake(
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
    fun `gir varsel om mulig forsørgingstillegg i månedsgrunnlaget uten lagret bekreftelse`() {
        val stønadsstart = LocalDate.of(2014, 12, 1)
        val historiskGrunnlag = grunnlag().let { grunnlag ->
            grunnlag.copy(
                stønadsavgrensning = grunnlag.stønadsavgrensning.copy(fraOgMed = stønadsstart),
            )
        }
        val service = service(TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)))
        val opprettet = service.opprett(command()).shouldBeRight()

        val måneder = service.hentMånedsgrunnlag(opprettet.id)!!.måneder
        måneder.map { it as HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse }
            .forEach {
                it.stønadsstart shouldBe stønadsstart
                it.kreverKontrollAvHistoriskForsørgingstillegg shouldBe true
                it.historiskBeløp shouldBe BigDecimal(9_000)
            }
    }

    @Test
    fun `avviser periode etter juni 2026 før sak og ordinære eller historiske vedtak slås opp`() {
        val sakRepo = mock<SakRepo>()
        val projeksjonRepo = mock<HistoriskInfotrygdTidslinjeRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val vedtakService = mock<VedtakServiceForInfotrygd>()
        val år = 2026
        val sisteHistoriskeMåned = juni(år)
        val førsteSuAppMåned = juli(år)
        val service = service(projeksjonRepo, revurderingRepo, sakRepo, vedtakService)

        service.opprett(
            command(Periode.create(sisteHistoriskeMåned.fraOgMed, førsteSuAppMåned.tilOgMed)),
        ).shouldBeLeft() shouldBe KunneIkkeOppretteHistoriskInfotrygdRevurderingService
            .PeriodenGårForbiSisteHistoriskeMåned(sisteHistoriskeMåned)

        verifyNoInteractions(sakRepo, projeksjonRepo, revurderingRepo, vedtakService)
    }

    @Test
    fun `tillater juni 2026 når ordinær SU starter i juli`() {
        val år = 2026
        val sisteHistoriskeMåned = juni(år)
        val førsteSuAppMåned = juli(år)
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag(tilOgMed = sisteHistoriskeMåned.tilOgMed))),
            revurderingRepo = revurderingRepo,
            vedtakServiceForInfotrygd = VedtakServiceForInfotrygd { førsteSuAppMåned },
        )

        val opprettet = service.opprett(command(sisteHistoriskeMåned)).shouldBeRight()

        opprettet.periode shouldBe sisteHistoriskeMåned
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `avviser periode som overlapper første innvilgede SU-app-måned`() {
        val førsteSuAppMåned = februar
        val projeksjonRepo = mock<HistoriskInfotrygdTidslinjeRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = projeksjonRepo,
            revurderingRepo = revurderingRepo,
            vedtakServiceForInfotrygd = VedtakServiceForInfotrygd { førsteSuAppMåned },
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .OverlapperInnvilgetSuAppYtelse(førsteSuAppMåned)

        verifyNoInteractions(projeksjonRepo, revurderingRepo)
    }

    @Test
    fun `avviser første måned uten historisk vedtak`() {
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(
                projeksjonId = projeksjonId,
                grunnlag = listOf(grunnlag(tilOgMed = januar.tilOgMed)),
            ),
            sakRepo = sakRepo,
            revurderingRepo = revurderingRepo,
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService
                .MånedManglerHistoriskVedtak(februar)

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 2])
    fun `avviser manglende eller tvetydig månedsbeløp før sak eller behandling opprettes`(antallMånedsbeløp: Int) {
        val historiskGrunnlag = grunnlag().let {
            it.copy(månedsbeløp = List(antallMånedsbeløp) { _ -> it.månedsbeløp.single() })
        }
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)),
            revurderingRepo = revurderingRepo,
            sakRepo = sakRepo,
        )
        val forventetFeil = if (antallMånedsbeløp == 0) {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.MånedManglerHistoriskMånedsbeløp(januar)
        } else {
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.MånedHarFlereHistoriskeMånedsbeløp(januar)
        }

        service.opprett(command()).shouldBeLeft() shouldBe forventetFeil

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @Test
    fun `opphørsvedtak trenger ikke månedsbeløp`() {
        val historiskGrunnlag = grunnlag().let {
            it.copy(
                vedtak = it.vedtak.copy(resultat = HistoriskResultat.OPPHØRT),
                månedsbeløp = emptyList(),
            )
        }
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(historiskGrunnlag)),
            revurderingRepo = revurderingRepo,
        )

        val opprettet = service.opprett(command()).shouldBeRight()

        opprettet.vedtakSomRevurderesMånedsvis.keys.toList() shouldBe periode.måneder()
        revurderingRepo.hent(opprettet.id) shouldBe opprettet
    }

    @Test
    fun `manglende projeksjon oppretter ikke sak eller behandling`() {
        val sakRepo = mock<SakRepo>()
        val revurderingRepo = mock<HistoriskInfotrygdRevurderingRepo>()
        val service = service(
            projeksjonRepo = mock(),
            revurderingRepo = revurderingRepo,
            sakRepo = sakRepo,
        )

        service.opprett(command()).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurderingService.FantIngenFullførtHistoriskProjeksjon

        verify(sakRepo, never()).opprettSak(any(), anyOrNull())
        verifyNoInteractions(revurderingRepo)
    }

    @Test
    fun `avslutter behandling`() {
        val revurderingRepo = HistoriskInfotrygdRevurderingRepoFake()
        val service = service(
            projeksjonRepo = TidslinjeRepoFake(projeksjonId, listOf(grunnlag())),
            revurderingRepo = revurderingRepo,
        )
        val opprettet = service.opprett(command()).shouldBeRight()

        val avsluttet = service.avslutt(
            id = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("S654321"),
            begrunnelse = "Behandlingen skal ikke gjennomføres.",
        ).shouldBeRight()

        avsluttet.status shouldBe HistoriskInfotrygdRevurderingStatus.AVSLUTTET
        revurderingRepo.hent(opprettet.id) shouldBe avsluttet
    }

    private fun service(
        projeksjonRepo: HistoriskInfotrygdTidslinjeRepo,
        revurderingRepo: HistoriskInfotrygdRevurderingRepo = HistoriskInfotrygdRevurderingRepoFake(),
        sakRepo: SakRepo = sakRepo(),
        vedtakServiceForInfotrygd: VedtakServiceForInfotrygd = VedtakServiceForInfotrygd { null },
    ) = HistoriskInfotrygdRevurderingService(
        sakRepo = sakRepo,
        tidslinjeRepo = projeksjonRepo,
        revurderingRepo = revurderingRepo,
        vedtakServiceForInfotrygd = vedtakServiceForInfotrygd,
        brevService = mock<BrevService>(),
        mottakerService = mock(),
        sessionFactory = mock(),
        clock = clock,
    )

    private fun sakRepo(): SakRepo = mock {
        on { hentSakInfoForIdent(fnr, Sakstype.ALDER, null) } doReturn sakInfo
        on { hentSakInfo(sakId) } doReturn sakInfo
    }

    private fun command(periode: Periode = HistoriskInfotrygdRevurderingServiceTest.periode) =
        OpprettHistoriskInfotrygdRevurderingCommand(
            fnr = fnr,
            periode = periode,
            saksbehandler = NavIdentBruker.Saksbehandler("S123456"),
        )

    private class HistoriskInfotrygdRevurderingRepoFake : HistoriskInfotrygdRevurderingRepo {
        private val behandlinger = mutableMapOf<HistoriskInfotrygdRevurderingId, HistoriskInfotrygdRevurdering>()
        private val vedtak = mutableMapOf<HistoriskInfotrygdRevurderingId, HistoriskInfotrygdRevurderingsvedtak>()
        private val transactionContext = mock<TransactionContext>()

        override fun opprett(
            revurdering: HistoriskInfotrygdRevurdering,
        ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> {
            behandlinger[revurdering.id] = revurdering
            return revurdering.right()
        }

        override fun lagre(
            revurdering: HistoriskInfotrygdRevurdering,
            transactionContext: TransactionContext,
        ) {
            behandlinger[revurdering.id] = revurdering
        }

        override fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering? = behandlinger[id]

        override fun hentForSak(sakId: UUID): List<HistoriskInfotrygdRevurdering> =
            behandlinger.values.filter { it.sakId == sakId }

        override fun lagreVedtak(vedtak: HistoriskInfotrygdRevurderingsvedtak) {
            this.vedtak[vedtak.revurderingId] = vedtak
        }

        override fun finnesVedtakForRevurdering(id: HistoriskInfotrygdRevurderingId) = id in vedtak

        override fun hentIverksatteMånedsresultater(
            sakId: UUID,
            periode: Periode,
        ) = emptyList<no.nav.su.se.bakover.domain.historisk.revurdering.IverksatteMånedsresultater>()

        override fun defaultTransactionContext(): TransactionContext = transactionContext
    }

    private class TidslinjeRepoFake(
        private val projeksjonId: UUID,
        private val grunnlag: List<HistoriskInfotrygdTidslinjegrunnlag>,
    ) : HistoriskInfotrygdTidslinjeRepo {
        override fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID = projeksjonId

        override fun hentOriginalTidslinjegrunnlag(
            projeksjonId: UUID,
            personident: String,
            periode: Periode,
        ): List<HistoriskInfotrygdTidslinjegrunnlag> = grunnlag
    }

    private companion object {
        val fnr: Fnr = Fnr.generer()
        val sakId: UUID = UUID.randomUUID()
        val projeksjonId: UUID = UUID.randomUUID()
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val periode: Periode = Periode.create(januar.fraOgMed, februar.tilOgMed)
        val stønadId = HistoriskStønadId(1)
        val sakInfo = SakInfo(sakId, Saksnummer(2021L), fnr, Sakstype.ALDER)
        val clock: Clock = Clock.fixed(Instant.parse("2020-03-01T10:00:00Z"), ZoneOffset.UTC)

        fun grunnlag(
            tilOgMed: LocalDate = februar.tilOgMed,
        ) = HistoriskInfotrygdTidslinjegrunnlag(
            vedtak = HistoriskInfotrygdTidslinjevedtak(
                stønadId = stønadId,
                vedtakId = HistoriskVedtakId(2),
                oppdragId = "oppdrag-1",
                fraOgMed = januar.fraOgMed,
                tilOgMed = tilOgMed,
                resultat = HistoriskResultat.INNVILGET,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                registrertTidspunkt = LocalDateTime.parse("2020-01-01T10:00:00"),
                endringskoder = emptyList(),
            ),
            stønadsavgrensning = HistoriskStønadsavgrensning(
                stønadId = stønadId,
                fraOgMed = januar.fraOgMed,
                tilOgMed = tilOgMed,
            ),
            månedsbeløp = listOf(
                HistoriskInfotrygdBeløpsperiode(
                    fraOgMed = januar.fraOgMed,
                    tilOgMed = tilOgMed,
                    sats = BigDecimal(10_000),
                    fradrag = BigDecimal(1_000),
                    fradragskoder = listOf("ARBM"),
                ),
            ),
        )
    }
}
