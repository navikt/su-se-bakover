package no.nav.su.se.bakover.web.komponenttest

import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.sisteIForrigeMåned
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleKomponentTest {

    @Test
    fun `oppretter kontrollsamtale, kall inn og annuller`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()
        val andreInnkalling = stønadStart.plusMonths(8).startOfMonth()
        val andreFrist = stønadStart.plusMonths(8).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).first()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            val andrePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).last()

            tikkendeKlokke.spolTil(andrePlanlagteKontrollsamtale.innkallingsdato)

            opprettIverksattRevurdering(
                sakId = sakId.toString(),
                fraOgMed = andreInnkalling.toString(),
                tilOgMed = stønadSlutt.toString(),
                leggTilUtenlandsopphold = { revurderingId ->
                    leggTilUtenlandsoppholdRevurdering(
                        sakId = sakId.toString(),
                        behandlingId = revurderingId,
                        fraOgMed = andreInnkalling.toString(),
                        tilOgMed = stønadSlutt.toString(),
                        vurdering = UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet.toString(),
                    )
                }
            )

            kontrollsamtaleService.hentForSak(sakId)
                .also { kontrollsamtaler ->
                    kontrollsamtaler.first().also {
                        it.innkallingsdato shouldBe førsteInnkalling
                        it.frist shouldBe førsteFrist
                        it.dokumentId shouldNot beNull()
                        it.status shouldBe Kontrollsamtalestatus.INNKALT
                        it.sakId shouldBe sakId
                    }
                    kontrollsamtaler.last().also {
                        it.innkallingsdato shouldBe andreInnkalling
                        it.frist shouldBe andreFrist
                        it.dokumentId shouldBe beNull()
                        it.status shouldBe Kontrollsamtalestatus.ANNULLERT
                        it.sakId shouldBe sakId
                    }
                }
        }
    }

    @Test
    fun `oppdaterer kontrollsamtale med journalpost for innsendt kontrollnotat hvis funnet`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale
            val utløptFristForKontrollsamtaleService = appComponents.services.utløptFristForKontrollsamtaleService

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).single()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.frist)

            utløptFristForKontrollsamtaleService.håndterKontrollsamtalerMedFristUtløpt(førstePlanlagteKontrollsamtale.frist)

            kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.find { it.id == førstePlanlagteKontrollsamtale.id }!!
                        .also {
                            it.innkallingsdato shouldBe førsteInnkalling
                            it.frist shouldBe førsteFrist
                            it.dokumentId shouldNot beNull()
                            it.status shouldBe Kontrollsamtalestatus.GJENNOMFØRT
                            it.sakId shouldBe sakId
                            it.journalpostIdKontrollnotat shouldBe JournalpostId("453812134")
                        }
                }
        }
    }

    @Test
    fun `stanser ytelse dersom kontrollnotat ikke er mottatt innen utløp av frist`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()
        val førsteInnkalling = stønadStart.plusMonths(4).startOfMonth()
        val førsteFrist = stønadStart.plusMonths(4).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
            clientsBuilder = { databaseRepos, klokke ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).copy(
                    journalpostClient = mock {
                        on { kontrollnotatMotatt(any(), any()) } doReturn ErKontrollNotatMottatt.Nei.right()
                    }
                )
            },
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale
            val utløptFristForKontrollsamtaleService = appComponents.services.utløptFristForKontrollsamtaleService

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).single()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.frist)

            utløptFristForKontrollsamtaleService.håndterKontrollsamtalerMedFristUtløpt(førstePlanlagteKontrollsamtale.frist)

            kontrollsamtaleService.hentForSak(sakId = sakId)
                .let { kontrollsamtaler ->
                    kontrollsamtaler.find { it.id == førstePlanlagteKontrollsamtale.id }!!
                        .also {
                            it.innkallingsdato shouldBe førsteInnkalling
                            it.frist shouldBe førsteFrist
                            it.dokumentId shouldNot beNull()
                            it.status shouldBe Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST
                            it.sakId shouldBe sakId
                            it.journalpostIdKontrollnotat shouldBe beNull()
                        }
                }

            appComponents.services.sak.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldHaveSize 1
                sak.vedtakstidslinje().also { vedtakstidslinje ->
                    Periode.create(
                        fraOgMed = stønadStart,
                        tilOgMed = førsteFrist.sisteIForrigeMåned()
                    ).måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak
                    }.all { it is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling }

                    Periode.create(
                        fraOgMed = førsteFrist.førsteINesteMåned(),
                        tilOgMed = stønadSlutt.endOfMonth()
                    ).måneder().map {
                        vedtakstidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak
                    }.all { it is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse }
                }
            }
        }
    }

    @Test
    fun `ruller tilbake endringer dersom noe går feil ved prosessering av utløpt kontrollsamtale`() {
        val tikkendeKlokke = TikkendeKlokke(LocalDate.now().fixedClock())
        val stønadStart = LocalDate.now().førsteINesteMåned()
        val stønadSlutt = stønadStart.plusMonths(11).endOfMonth()

        withKomptestApplication(
            clock = tikkendeKlokke,
            repoBuilder = { dataSource, klokke, satsFactory ->
                SharedRegressionTestData.databaseRepos(
                    dataSource = dataSource,
                    clock = klokke,
                    satsFactory = satsFactory,
                ).let {
                    // overstyrer vedtakrepo slik at vi kan kaste exception ved lagring av stansvedtak
                    it.copy(
                        vedtakRepo = object : VedtakRepo by it.vedtakRepo {
                            override fun lagre(vedtak: Vedtak, sessionContext: TransactionContext) {
                                if (vedtak is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
                                    throw NullPointerException("tull og fjas")
                                } else {
                                    it.vedtakRepo.lagre(vedtak, sessionContext)
                                }
                            }
                        }
                    )
                }
            },
            clientsBuilder = { databaseRepos, klokke ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).copy(
                    journalpostClient = mock {
                        on { kontrollnotatMotatt(any(), any()) } doReturn ErKontrollNotatMottatt.Nei.right()
                    }
                )
            },
        ) { appComponents ->
            val kontrollsamtaleService = appComponents.services.kontrollsamtale
            val utløptFristForKontrollsamtaleService = appComponents.services.utløptFristForKontrollsamtaleService

            val sakId = innvilgSøknad(
                fraOgMed = stønadStart,
                tilOgMed = stønadSlutt
            )

            val førstePlanlagteKontrollsamtale = kontrollsamtaleService.hentForSak(sakId = sakId).single()

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.innkallingsdato)

            kontrollsamtaleService.kallInn(
                sakId = sakId,
                kontrollsamtale = førstePlanlagteKontrollsamtale
            )

            tikkendeKlokke.spolTil(førstePlanlagteKontrollsamtale.frist)

            utløptFristForKontrollsamtaleService.håndterKontrollsamtalerMedFristUtløpt(førstePlanlagteKontrollsamtale.frist)

            appComponents.services.sak.hentSak(sakId).getOrFail().also { sak ->
                sak.revurderinger shouldHaveSize 0
                sak.vedtakstidslinje().let { tidslinje ->
                    Periode.create(stønadStart, stønadSlutt).måneder().map {
                        tidslinje.gjeldendeForDato(it.fraOgMed)!!.originaltVedtak
                    }.all { it is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling }
                }
                kontrollsamtaleService.hentForSak(sak.id)
                    .single { it.id == førstePlanlagteKontrollsamtale.id }
                    .status shouldBe Kontrollsamtalestatus.INNKALT
            }
        }
    }

    private fun ApplicationTestBuilder.innvilgSøknad(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): UUID {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = fraOgMed.toString(),
            tilOgMed = tilOgMed.toString(),
        ).let {
            hentSak(BehandlingJson.hentSakId(it)).let { sakJson ->
                UUID.fromString(hentSakId(sakJson))
            }
        }
    }
}
