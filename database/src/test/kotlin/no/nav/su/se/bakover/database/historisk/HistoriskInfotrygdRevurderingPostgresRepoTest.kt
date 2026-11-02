package no.nav.su.se.bakover.database.historisk

import behandling.revurdering.domain.Opphørsgrunn
import dokument.domain.Brevtype
import dokument.domain.Dokument
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
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdManueltOpphør
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.util.PSQLException
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
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
            periode = januar,
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lagrer beregnet historisk revurdering med regeltre per måned`(manueltOpphør: Boolean) {
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
                    bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                    fradrag = emptyList(),
                    manueltOpphør = if (manueltOpphør) HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE) else null,
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

        val utenForhåndsvarsel = oppdatert.velgÅIkkeSendeForhåndsvarsel(
            saksbehandler = saksbehandler,
            tidspunkt = opprettet.plusUnits(2),
        ).shouldBeRight()
        repo.lagre(utenForhåndsvarsel)
        repo.hent(revurdering.id) shouldBe utenForhåndsvarsel
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
        val tjuetjue = 2020
        val januar = januar(tjuetjue)
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = januar,
            saksbehandler = saksbehandler,
            tidspunkt = opprettet,
            gjeldendeVedtaksdata = GjeldendeHistoriskInfotrygdVedtaksdata(
                projeksjonId = projeksjonId,
                periode = januar,
                tidslinje = linkedMapOf(
                    januar to ingenYtelse(projeksjonId, januar),
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
                    januar to HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                        måned = januar,
                        opprinneligStønadId = stønadId,
                        opprinneligVedtakId = vedtakId,
                        oppdragId = "oppdrag-1",
                        bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                        sats = BigDecimal(10_000),
                        fradrag = emptyList(),
                        benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE
                            .benyttRegelspesifisering("Test"),
                    ),
                ),
            ),
        )

        repo.finnesVedtakForRevurdering(revurdering.id) shouldBe false
        repo.lagreVedtak(vedtak)
        repo.finnesVedtakForRevurdering(revurdering.id) shouldBe true

        repo.hentIverksatteMånedsresultater(sakId, januar) shouldBe
            listOf(vedtak.tilIverksatteMånedsresultater())
        repo.hentIverksatteMånedsresultater(sakId, februar(tjuetjue)) shouldBe emptyList()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `bevarer uavrundet sats og fribeløp samt regeltre for ytelse og opphør når ektefellen er ufør flyktning`(
        opphør: Boolean,
    ) {
        val helper = TestDataHelper(dataSource)
        val sakId = UUID.randomUUID()
        helper.sakRepo.opprettSak(SakInfoNy(sakId = sakId, fnr = Fnr.generer(), type = Sakstype.ALDER))
        val projeksjonId = fullførtProjeksjon(helper)
        val repo = HistoriskInfotrygdRevurderingPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val måned = januar(2021)
        val tidspunkt = Tidspunkt.create(Instant.parse("2021-02-01T10:00:00Z"))
        val bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING
        val satser = SatsFactoryForSupplerendeStønad().gjeldende(måned.fraOgMed)
        val sats = satser.ordinærAlder(måned).satsForMåned
        val epsFribeløp = satser.ordinærUføre(måned).satsForMåned
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = projeksjonId,
            periode = måned,
            tidslinje = linkedMapOf(måned to ingenYtelse(projeksjonId, måned)),
        )
        val revurdering = HistoriskInfotrygdRevurdering.opprett(
            sakId = sakId,
            projeksjonId = projeksjonId,
            periode = måned,
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt,
            gjeldendeVedtaksdata = gjeldende,
        ).shouldBeRight()
        repo.opprett(revurdering).shouldBeRight()
        val beregning = gjeldende.beregnRevurdering(
            grunnlag = listOf(
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = måned,
                    bosituasjon = bosituasjon,
                    fradrag = listOf(
                        FradragForMåned(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = if (opphør) 40_000.0 else 100.6,
                            måned = måned,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        FradragForMåned(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 20_000.0,
                            måned = måned,
                            tilhører = FradragTilhører.EPS,
                        ),
                    ),
                ),
            ),
            satsFactory = satser,
        ).shouldBeRight()
        val oppdatert = revurdering.oppdaterGrunnlag(
            beregning = beregning,
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(1),
        ).shouldBeRight()

        repo.lagre(oppdatert)
        val hentet = requireNotNull(repo.hent(revurdering.id))
        hentet shouldBe oppdatert
        val resultat = requireNotNull(hentet.beregning).månedsresultater.getValue(måned)
        resultat.bosituasjon shouldBe bosituasjon
        resultat.sats shouldBe sats
        resultat.epsFribeløp shouldBe epsFribeløp
        (sats.compareTo(epsFribeløp) != 0) shouldBe true
        (sats.stripTrailingZeros().scale() > 0) shouldBe true
        (epsFribeløp.stripTrailingZeros().scale() > 0) shouldBe true
        resultat.epsFradrag.compareTo(BigDecimal("743.31")) shouldBe 0
        resultat.fradrag shouldBe beregning.månedsresultater.getValue(måned).fradrag
        resultat.benyttetRegel shouldBe beregning.månedsresultater.getValue(måned).benyttetRegel
        if (opphør) {
            resultat as HistoriskInfotrygdRevurdertMånedsresultat.Opphør
            resultat.opphørsgrunn shouldBe Opphørsgrunn.FOR_HØY_INNTEKT
            resultat.manueltOpphør shouldBe false
        } else {
            resultat as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            resultat.beløp shouldBe BigDecimal("13966")
        }

        val vedtak = HistoriskInfotrygdRevurderingsvedtak(
            id = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID()),
            revurderingId = revurdering.id,
            sakId = sakId,
            utbetalingId = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c"),
            iverksatt = tidspunkt.plusUnits(2),
            attestant = NavIdentBruker.Attestant("A123456"),
            beregning = beregning,
        )
        repo.lagreVedtak(vedtak)
        repo.hentIverksatteMånedsresultater(sakId, måned) shouldBe
            listOf(vedtak.tilIverksatteMånedsresultater())
    }

    @Test
    fun `historisk forhåndsvarsel lagres med egen referanse og finnes bare via historisk oppslag`() {
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
            historiskRevurderingId = revurdering.id.value,
        )
        val dokument = dokumentUtenMetadataInformasjonViktig().copy(
            brevtype = Brevtype.FORHANDSVARSEL,
        ).leggTilMetadata(metadata, distribueringsadresse = null)
        dokumentRepo.lagre(dokument)

        dokumentRepo.hentDokument(dokument.id)!!.metadata shouldBe metadata
        dokumentRepo.hentForRevurdering(revurdering.id.value) shouldBe emptyList()
        dokumentRepo.hentForHistoriskRevurdering(revurdering.id.value).map { it.id } shouldBe listOf(dokument.id)
        dokumentRepo.hentForSak(sakId).map { it.id } shouldBe listOf(dokument.id)

        val dokumentMedFeilReferanse = dokument.copy(
            id = UUID.randomUUID(),
            metadata = Dokument.Metadata(sakId = sakId, revurderingId = revurdering.id.value),
        )
        val fremmednøkkelbrudd = "23503"
        assertThrows<PSQLException> {
            dokumentRepo.lagre(dokumentMedFeilReferanse)
        }.sqlState shouldBe fremmednøkkelbrudd
        dokumentRepo.hentDokument(dokumentMedFeilReferanse.id) shouldBe null

        val ukjentHistoriskRevurderingId = UUID.randomUUID()
        val dokumentUtenBehandling = dokument.copy(
            id = UUID.randomUUID(),
            metadata = metadata.copy(historiskRevurderingId = ukjentHistoriskRevurderingId),
        )
        assertThrows<PSQLException> {
            dokumentRepo.lagre(dokumentUtenBehandling)
        }.sqlState shouldBe fremmednøkkelbrudd
        dokumentRepo.hentDokument(dokumentUtenBehandling.id) shouldBe null

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
            januar to ingenYtelse(projeksjonId, januar),
            februar to ingenYtelse(projeksjonId, februar),
        ),
    )

    private fun ingenYtelse(
        projeksjonId: UUID,
        måned: no.nav.su.se.bakover.common.tid.periode.Måned,
    ) = GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        opprinneligStønadId = stønadId,
        opprinneligVedtakId = vedtakId,
    )

    private companion object {
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val stønadId = HistoriskStønadId(1)
        val vedtakId = HistoriskVedtakId(2)
        val periode: Periode = Periode.create(januar.fraOgMed, februar.tilOgMed)
        val opprettet: Tidspunkt = Tidspunkt.create(Instant.parse("2020-03-01T10:00:00Z"))
        val saksbehandler = NavIdentBruker.Saksbehandler("S123456")
    }
}
