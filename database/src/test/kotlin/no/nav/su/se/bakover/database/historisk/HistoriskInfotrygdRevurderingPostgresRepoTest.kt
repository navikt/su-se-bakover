package no.nav.su.se.bakover.database.historisk

import behandling.revurdering.domain.Opphørsgrunn
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.DokumentRevurderingstype
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
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdManueltOpphør
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.beregnRevurdering
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.postgresql.util.PSQLException
import satser.domain.historisk.HistoriskInfotrygdSatskategori
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
    fun `lagrer beregnet historisk revurdering`() {
        val helper = TestDataHelper(dataSource)
        val sakId = UUID.randomUUID()
        helper.sakRepo.opprettSak(SakInfoNy(sakId = sakId, fnr = Fnr.generer(), type = Sakstype.ALDER))
        val projeksjonId = fullførtProjeksjon(helper)
        val repo = HistoriskInfotrygdRevurderingPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val gjeldende = gjeldendeVedtaksdata(projeksjonId)
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = periode,
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = gjeldende,
        ).shouldBeRight()
        repo.opprett(revurdering).shouldBeRight()

        val beregning = gjeldende.beregnRevurdering(
            periode.måneder().map { måned ->
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = måned,
                    satskategori = HistoriskInfotrygdSatskategori.EN,
                    fradrag = emptyList(),
                    manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE),
                )
            },
        ).shouldBeRight()
        val oppdatert = revurdering.oppdaterGrunnlag(
            beregning = beregning,
            saksbehandler = saksbehandler,
            tidspunkt = opprettet.plusUnits(1),
        ).shouldBeRight()

        repo.lagre(oppdatert)
        repo.hent(revurdering.id) shouldBe oppdatert
    }

    @Test
    fun `henter iverksatte månedsresultater for sak og periode`() {
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
        val vedtak = HistoriskInfotrygdRevurderingsvedtak(
            id = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID()),
            revurderingId = revurdering.id,
            sakId = sakId,
            utbetalingId = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c"),
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

        repo.finnesVedtakForRevurdering(revurdering.id) shouldBe false
        repo.lagreVedtak(vedtak)
        repo.finnesVedtakForRevurdering(revurdering.id) shouldBe true

        repo.hentIverksatteMånedsresultater(sakId, januar(2020)) shouldBe
            listOf(vedtak.tilIverksatteMånedsresultater())
        repo.hentIverksatteMånedsresultater(sakId, februar(2020)) shouldBe emptyList()
    }

    @Test
    fun `historiske dokumenter lagres med type og finnes bare via historisk oppslag`() {
        val helper = TestDataHelper(dataSource)
        val sakId = UUID.randomUUID()
        helper.sakRepo.opprettSak(SakInfoNy(sakId, Fnr.generer(), Sakstype.ALDER))
        val projeksjonId = fullførtProjeksjon(helper)
        val repo = HistoriskInfotrygdRevurderingPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = periode,
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata(projeksjonId),
        ).shouldBeRight()
        repo.opprett(revurdering).shouldBeRight()
        val dokumentRepo = helper.databaseRepos.dokumentRepo
        val metadata = Dokument.Metadata(
            sakId = sakId,
            revurderingId = revurdering.id.value,
            revurderingstype = DokumentRevurderingstype.HISTORISK_INFOTRYGD,
        )
        val dokument = dokumentUtenMetadataInformasjonViktig().copy(
            brevtype = Brevtype.FORHANDSVARSEL,
        ).leggTilMetadata(metadata, distribueringsadresse = null)
        dokumentRepo.lagre(dokument)

        dokumentRepo.hentDokument(dokument.id)!!.metadata shouldBe metadata
        dokumentRepo.hentForRevurdering(revurdering.id.value) shouldBe emptyList()
        dokumentRepo.hentForRevurdering(
            revurdering.id.value,
            DokumentRevurderingstype.HISTORISK_INFOTRYGD,
        ).map { it.id } shouldBe listOf(dokument.id)
        dokumentRepo.hentForSak(sakId).map { it.id } shouldBe listOf(dokument.id)

        assertThrows<PSQLException> {
            dokumentRepo.lagre(
                dokument.copy(
                    id = UUID.randomUUID(),
                    metadata = metadata.copy(revurderingstype = DokumentRevurderingstype.ORDINAER),
                ),
            )
        }
        repo.slettAlleForLokalSeed()
        dokumentRepo.hentForSak(sakId) shouldBe emptyList()
        repo.hent(revurdering.id) shouldBe null
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
