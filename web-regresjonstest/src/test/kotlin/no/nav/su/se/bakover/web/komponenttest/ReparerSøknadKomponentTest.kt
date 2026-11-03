package no.nav.su.se.bakover.web.komponenttest

import arrow.core.right
import dokument.domain.brev.Brevvalg
import dokument.domain.forsteside.PostForstesideResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.between
import no.nav.su.se.bakover.common.tid.fixedClock
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.avvisSøknadUtenBrev
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class ReparerSøknadKomponentTest(private val dataSource: DataSource) {
    @ParameterizedTest
    @EnumSource(Sakstype::class)
    fun `reparerer alle steg uten å duplisere behandling eller statistikk`(sakstype: Sakstype) {
        val reparasjonsklokke = tikkendeFixedClock()
        val registrertTid = fixedTidspunkt.minus(10, ChronoUnit.DAYS)
        val helper = TestDataHelper(dataSource, clock = registrertTid.fixedClock())
        val søknadsinnhold = when (sakstype) {
            Sakstype.ALDER -> søknadsinnholdAlder()
            Sakstype.UFØRE -> søknadinnholdUføre()
        }
        val fnr = søknadsinnhold.personopplysninger.fnr
        val nySak = helper.persisterSakMedSøknadUtenJournalføringOgOppgave(fnr = fnr, søknadInnhold = søknadsinnhold)
        val journalførtSøknad = helper.persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(
            sakId = nySak.id,
            fnr = fnr,
            søknadInnhold = søknadsinnhold,
        )
        val søknadMedOppgave = helper.persisterJournalførtSøknadMedOppgave(
            sakId = nySak.id,
            fnr = fnr,
            søknadInnhold = søknadsinnhold,
        ).second
        val lukketSøknad = helper.persisterJournalførtSøknadMedOppgave(
            sakId = nySak.id,
            fnr = fnr,
            søknadInnhold = søknadsinnhold,
        ).second.let {
            it.lukk(
                trekkSøknad(
                    søknadId = it.id,
                    lukketTidspunkt = registrertTid,
                    trukketDato = registrertTid.toLocalDate(zoneIdOslo),
                ),
            )
        }
        helper.søknadRepo.lukkSøknad(lukketSøknad)
        val søknadIder = setOf(nySak.søknad.id, journalførtSøknad.id, søknadMedOppgave.id)
        val publisher = mock<KafkaPublisher>()
        val pdfBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("FoerstesideSoknadUfor.pdf")).use { it.readAllBytes() }
        val førsteside = PostForstesideResponse(pdfBytes, "løpenummer")

        withKomptestApplication(
            dataSource,
            clock = reparasjonsklokke,
            clientsBuilder = { repos, clock, config ->
                TestClientsBuilder(clock = clock, databaseRepos = repos).build(config).copy(
                    kafkaPublisher = publisher,
                    forstesideGeneratorClient = mock {
                        on { genererForsteside(any()) } doReturn førsteside.right()
                    },
                )
            },
        ) { app ->
            val reparasjonStart = Tidspunkt.now(reparasjonsklokke)
            val resultat = app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
            val reparasjonSlutt = Tidspunkt.now(reparasjonsklokke)
            resultat.journalpostResultat.single().getOrFail().id shouldBe nySak.søknad.id
            resultat.oppgaveResultat.map { it.getOrFail().id }.toSet() shouldBe setOf(nySak.søknad.id, journalførtSøknad.id)
            val behandlinger = resultat.behandlingResultat.map { it.getOrFail() }
            behandlinger.map { it.søknad.id }.toSet() shouldBe søknadIder
            app.databaseRepos.søknad.hentSøknad(lukketSøknad.id) shouldBe lukketSøknad
            behandlinger.forEach {
                it.shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Uavklart>()
                it.opprettet.between(reparasjonStart, reparasjonSlutt) shouldBe true
                it.søknad.opprettet shouldBe registrertTid
                it.saksbehandler shouldBe null
            }
            val statistikk = app.databaseRepos.sakStatistikkRepo.hentSakStatistikk(nySak.id)
            statistikk.map { it.behandlingId }.toSet() shouldBe behandlinger.map { it.id.value }.toSet()
            statistikk.size shouldBe søknadIder.size
            statistikk.forEach { rad ->
                val behandling = behandlinger.single { it.id.value == rad.behandlingId }
                rad.mottattTid shouldBe behandling.opprettet
                rad.registrertTid shouldBe behandling.opprettet
                rad.funksjonellTid.between(reparasjonStart, reparasjonSlutt) shouldBe true
            }

            val nyKjøring = app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
            nyKjøring.journalpostResultat shouldBe emptyList()
            nyKjøring.oppgaveResultat shouldBe emptyList()
            nyKjøring.behandlingResultat shouldBe emptyList()
            app.databaseRepos.søknadsbehandling.hentForSak(nySak.id).toSet() shouldBe behandlinger.toSet()
            app.databaseRepos.sakStatistikkRepo.hentSakStatistikk(nySak.id).toSet() shouldBe statistikk.toSet()
            verify(publisher, times(søknadIder.size)).publiser(any(), any())
        }
    }

    @ParameterizedTest
    @EnumSource(Sakstype::class)
    fun `søknad med reparert behandling kan avvises uten brev`(sakstype: Sakstype) {
        val registrertTid = fixedTidspunkt.minus(10, ChronoUnit.MINUTES)
        val søknadsinnhold = when (sakstype) {
            Sakstype.ALDER -> søknadsinnholdAlder()
            Sakstype.UFØRE -> søknadinnholdUføre()
        }
        val (_, søknad) = TestDataHelper(dataSource, clock = registrertTid.fixedClock()).persisterJournalførtSøknadMedOppgave(
            fnr = søknadsinnhold.personopplysninger.fnr,
            søknadInnhold = søknadsinnhold,
        )

        withKomptestApplication(dataSource, clock = fixedClock) { app ->
            val behandling = app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling().behandlingResultat.single().getOrFail()
            val command = avvisSøknadUtenBrev(søknadId = søknad.id)

            app.services.lukkSøknad.lukkSøknad(command)

            val lukketSøknad = app.databaseRepos.søknad.hentSøknad(søknad.id)
                .shouldBeTypeOf<Søknad.Journalført.MedOppgave.Lukket.Avvist>()
            lukketSøknad.lukketTidspunkt shouldBe command.lukketTidspunkt
            lukketSøknad.lukketAv shouldBe command.saksbehandler
            lukketSøknad.brevvalg.shouldBeTypeOf<Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev>()

            val lukketBehandling = app.databaseRepos.søknadsbehandling.hentForSøknad(søknad.id)
                .shouldBeTypeOf<LukketSøknadsbehandling>()
            lukketBehandling.id shouldBe behandling.id
            lukketBehandling.søknad.shouldBeTypeOf<Søknad.Journalført.MedOppgave.Lukket.Avvist>()
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 5])
    fun `reparasjonen venter til søknadens opprettelsestid er mer enn fem minutter gammel`(alderIMinutter: Long) {
        val opprettet = fixedTidspunkt.minus(alderIMinutter, ChronoUnit.MINUTES)
        val helper = TestDataHelper(dataSource, clock = opprettet.fixedClock())
        val sak = helper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        helper.persisterJournalførtSøknadUtenOppgaveForEksisterendeSak(sakId = sak.id, fnr = sak.fnr)
        helper.persisterJournalførtSøknadMedOppgave(sakId = sak.id, fnr = sak.fnr)
        val publisher = mock<KafkaPublisher>()
        withKomptestApplication(
            dataSource,
            clock = fixedClock,
            clientsBuilder = { repos, clock, config ->
                TestClientsBuilder(clock = clock, databaseRepos = repos).build(config).copy(kafkaPublisher = publisher)
            },
        ) { app ->
            val resultat = app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
            resultat.journalpostResultat shouldBe emptyList()
            resultat.oppgaveResultat shouldBe emptyList()
            resultat.behandlingResultat shouldBe emptyList()
            app.databaseRepos.søknadsbehandling.hentForSak(sak.id) shouldBe emptyList()
            app.databaseRepos.sakStatistikkRepo.hentSakStatistikk(sak.id) shouldBe emptyList()
            verifyNoInteractions(publisher)
        }
    }

    @Test
    fun `feil ved lagring av statistikk ruller tilbake behandlingen og lar neste kjøring reparere søknaden`() {
        val opprettet = fixedTidspunkt.minus(10, ChronoUnit.MINUTES)
        val (sak, _) = TestDataHelper(dataSource, clock = opprettet.fixedClock()).persisterJournalførtSøknadMedOppgave()
        val feilVedNesteLagring = AtomicBoolean(true)
        val publisher = mock<KafkaPublisher>()
        val feil = IllegalStateException("Statistikklagring feilet")
        withKomptestApplication(
            dataSource,
            clock = fixedClock,
            repoBuilder = { ds, clock, satsFactory ->
                val repos = SharedRegressionTestData.databaseRepos(ds, clock, satsFactory)
                repos.copy(
                    sakStatistikkRepo = object : SakStatistikkRepo by repos.sakStatistikkRepo {
                        override fun lagreSakStatistikk(sakStatistikk: SakStatistikk, sessionContext: SessionContext?) {
                            repos.sakStatistikkRepo.lagreSakStatistikk(sakStatistikk, sessionContext)
                            if (feilVedNesteLagring.getAndSet(false)) throw feil
                        }
                    },
                )
            },
            clientsBuilder = { repos, clock, config ->
                TestClientsBuilder(clock = clock, databaseRepos = repos).build(config).copy(kafkaPublisher = publisher)
            },
        ) { app ->
            shouldThrow<IllegalStateException> {
                app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
            } shouldBe feil
            app.databaseRepos.søknadsbehandling.hentForSak(sak.id) shouldBe emptyList()
            app.databaseRepos.sakStatistikkRepo.hentSakStatistikk(sak.id) shouldBe emptyList()
            verifyNoInteractions(publisher)

            val behandling = app.services.søknad.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling().behandlingResultat.single().getOrFail()
            app.databaseRepos.søknadsbehandling.hentForSak(sak.id) shouldBe listOf(behandling)
            app.databaseRepos.sakStatistikkRepo.hentSakStatistikk(sak.id).single().behandlingId shouldBe behandling.id.value
            verify(publisher).publiser(any(), any())
        }
    }
}
