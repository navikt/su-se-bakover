package no.nav.su.se.bakover.database.historisk

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.sak.SakInfoNy
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class HistoriskInfotrygdRevurderingPostgresRepoTest(
    private val dataSource: DataSource,
) {
    @Test
    fun `lagrer vedtaksreferanser og hindrer overlappende åpne behandlinger`() {
        val helper = TestDataHelper(dataSource)
        val sakId = UUID.randomUUID()
        val fnr = Fnr.generer()
        helper.sakRepo.opprettSak(SakInfoNy(sakId = sakId, fnr = fnr, type = Sakstype.ALDER))
        val projeksjonId = fullførtProjeksjon(helper)
        val repo = HistoriskInfotrygdRevurderingPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val gjeldendeVedtaksdata = gjeldendeVedtaksdata(projeksjonId)
        val første = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = periode,
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        ).shouldBeRight()

        repo.opprett(første).shouldBeRight()
        repo.hent(første.id) shouldBe første

        val overlappende = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = januar(2020),
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        ).shouldBeRight()
        repo.opprett(overlappende).shouldBeLeft() shouldBe
            KunneIkkeOppretteHistoriskInfotrygdRevurdering.OverlapperÅpenBehandling(
                eksisterendeRevurderingId = første.id,
                sakId = første.sakId,
            )

        val avsluttet = første.avslutt(
            saksbehandler = saksbehandler,
            begrunnelse = "Behandlingen skal ikke gjennomføres.",
            tidspunkt = opprettet.plusUnits(1),
        ).shouldBeRight()
        repo.lagre(avsluttet)
        repo.hent(første.id) shouldBe avsluttet

        repo.opprett(overlappende).shouldBeRight()
        repo.hent(overlappende.id) shouldBe overlappende
    }

    @Test
    fun `finner historisk revurderingsvedtak fra utbetalingId`() {
        val helper = TestDataHelper(dataSource)
        val sakId = UUID.randomUUID()
        helper.sakRepo.opprettSak(
            SakInfoNy(
                sakId = sakId,
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
        )
        val projeksjonId = fullførtProjeksjon(helper)
        val repo = HistoriskInfotrygdRevurderingPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = januar(2020),
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = GjeldendeHistoriskInfotrygdVedtaksdata(
                projeksjonId = projeksjonId,
                periode = januar(2020),
                tidslinje = linkedMapOf(
                    januar(2020) to ingenYtelse(projeksjonId, januar(2020)),
                ),
            ),
        ).shouldBeRight()
        repo.opprett(revurdering).shouldBeRight()
        val utbetalingId = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
        val vedtak = HistoriskInfotrygdRevurderingsvedtak(
            id = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID()),
            revurderingId = revurdering.id,
            sakId = sakId,
            utbetalingId = utbetalingId,
            iverksatt = opprettet.plusUnits(1),
            attestant = NavIdentBruker.Attestant("A123456"),
            beregning = HistoriskInfotrygdBeregning(
                månedsresultater = linkedMapOf(
                    januar(2020) to HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                        måned = januar(2020),
                        opprinneligStønadId = HistoriskStønadId(1),
                        opprinneligVedtakId = HistoriskVedtakId(2),
                        oppdragId = "oppdrag-1",
                        bosituasjon = HistoriskBosituasjon.ENSLIG,
                        sats = BigDecimal(10_000),
                        fradrag = emptyList(),
                    ),
                ),
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING
                    .benyttRegelspesifisering("Test"),
            ),
        )

        repo.lagreVedtak(vedtak)

        repo.hentVedtakForUtbetaling(utbetalingId) shouldBe vedtak
        repo.hentVedtakForUtbetaling(
            UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604"),
        ) shouldBe null
    }

    private fun fullførtProjeksjon(helper: TestDataHelper): UUID {
        val importRepo = HistoriskImportPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val historiskImport = importRepo.opprettImport(
            listOf(NyHistoriskTabellimport(InfotrygdTabeller.T_STONAD, 0, listOf("STONAD_ID"))),
        )
        importRepo.fullførImport(historiskImport.id)
        return HistoriskAlderProjeksjonPostgresRepo(helper.sessionFactory, helper.dbMetrics)
            .let { repo ->
                repo.startProjeksjon(historiskImport.id).also {
                    repo.fullførProjeksjon(it, antallStønader = 0)
                }
            }
    }

    private fun gjeldendeVedtaksdata(projeksjonId: UUID) = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = projeksjonId,
        periode = periode,
        tidslinje = linkedMapOf(
            januar(2020) to ingenYtelse(projeksjonId, januar(2020)),
            februar(2020) to ingenYtelse(projeksjonId, februar(2020)),
        ),
    )

    private fun ingenYtelse(
        projeksjonId: UUID,
        måned: no.nav.su.se.bakover.common.tid.periode.Måned,
    ) = GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        opprinneligStønadId = HistoriskStønadId(1),
        opprinneligVedtakId = HistoriskVedtakId(2),
    )

    private companion object {
        val periode: Periode = Periode.create(januar(2020).fraOgMed, februar(2020).tilOgMed)
        val opprettet: Tidspunkt = Tidspunkt.create(Instant.parse("2020-03-01T10:00:00Z"))
        val saksbehandler = NavIdentBruker.Saksbehandler("S123456")
    }
}
