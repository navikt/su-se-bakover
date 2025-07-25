package no.nav.su.se.bakover.database.sak

import arrow.core.getOrElse
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag.Behandlingstype
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.TestDataHelper.Companion.søknadNy
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            val opprettet: Sak = repo.hentSak(nySak.fnr, Sakstype.UFØRE)!!
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(opprettet.fnr, Sakstype.UFØRE)!!

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe nySak.fnr
            opprettet.id shouldBe nySak.id
            opprettet.opprettet shouldBe nySak.opprettet
            opprettet.søknadNy() shouldBe nySak.søknad
        }
    }

    @Test
    fun `hent sakId fra fnr liste med 1 element`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val nySak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            repo.hentSakInfoForIdent(nySak.fnr, Sakstype.UFØRE) shouldBe SakInfo(
                nySak.id,
                nySak.saksnummer,
                nySak.fnr,
                nySak.type,
            )
        }
    }

    @Test
    fun `hent sakinfo for bruker uten sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            repo.hentSakInfoForIdent(Fnr.generer(), Sakstype.UFØRE) shouldBe null
            repo.hentSakInfoForIdent(Fnr.generer(), Sakstype.ALDER) shouldBe null
        }
    }

    @Test
    fun `Mapper riktig for omgjøring for avsluttet søknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val (sak, original) = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()
            val omgjøring = original.opprettNySøknadsbehandling(
                nyOppgaveId = OppgaveId(value = "ny oppgaveId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "ny saksbehandler"),
                clock = fixedClock,
                omgjøringsårsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
            ).getOrElse { throw RuntimeException("feil") }
            testDataHelper.databaseRepos.søknadsbehandling.lagre(omgjøring)
            val åpneBehandlinger = repo.hentÅpneBehandlinger()
            åpneBehandlinger.size shouldBe 1
            åpneBehandlinger.first().behandlingstype shouldBe Behandlingstype.OMGJØRING
        }
    }

    @Test
    fun `Mapper riktig for revurdering omgjøring `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.revurderingRepo

            val (_, revurdering) = testDataHelper.persisterRevurderingOpprettet(
                sakOgVedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                    .let { it.first to it.second },
                sakOgRevurdering = { (sak, vedtak) ->
                    opprettetRevurdering(
                        sakOgVedtakSomKanRevurderes = Pair(sak, vedtak),
                        omgjøringsgrunn = Omgjøringsgrunn.NYE_OPPLYSNINGER,
                        revurderingsårsak = Revurderingsårsak(
                            Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
                            Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
                        ),
                    )
                },

            )

            repo.lagre(revurdering)
            val reposak = testDataHelper.sakRepo

            val åpneBehandlinger = reposak.hentÅpneBehandlinger()
            åpneBehandlinger.size shouldBe 1
            åpneBehandlinger.first().behandlingstype shouldBe Behandlingstype.REVURDERING_OMGJØRING
        }
    }

    @Test
    fun `henter ikke ut lukkede eller avsluttede behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo

            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sak.id)
            testDataHelper.persisterRevurderingAvsluttet()
            testDataHelper.persisterKlageAvsluttet()

            val åpneBehandlinger = repo.hentÅpneBehandlinger()

            åpneBehandlinger.size shouldBe 1

            åpneBehandlinger shouldBe listOf(
                Behandlingssammendrag(
                    saksnummer = Saksnummer(2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    behandlingStartet = null,
                    status = Behandlingssammendrag.Behandlingsstatus.NY_SØKNAD,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
            )
        }
    }

    @Test
    fun `henter sakinfo for et gitt fnr`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val (sak) = testDataHelper.persisterSøknadsbehandlingIverksatt()
            val hentetSakInfo = repo.hentSakInfo(sak.fnr).first()

            hentetSakInfo.fnr shouldBe sak.fnr
            hentetSakInfo.sakId shouldBe sak.id
            hentetSakInfo.saksnummer shouldBe sak.saksnummer
            hentetSakInfo.type shouldBe sak.type
        }
    }

    @Test
    fun `Henter alle restanser for alle saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(sakId = sak.id)
            testDataHelper.persisterSøknadsbehandlingAvsluttet(sakId = sak.id)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val underkjent = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagMedBeregning().second
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().second
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            val (_, opprettetRevurdering) =
                testDataHelper.persisterRevurderingOpprettet(
                    sakOgVedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                        .let { it.first to it.second },
                )
            val (_, tilAttesteringRevurdering) = testDataHelper.persisterRevurderingTilAttesteringInnvilget()
            val (_, underkjentRevurdering) = testDataHelper.persisterRevurderingUnderkjentInnvilget()
            testDataHelper.persisterRevurderingIverksattInnvilget()

            val opprettetKlage = testDataHelper.persisterKlageOpprettet()
            val vurdertKlage = testDataHelper.persisterKlageVurdertPåbegynt()
            val klageTilAttestering = testDataHelper.persisterKlageTilAttesteringAvvist()
            testDataHelper.persisterKlageIverksattAvvist()

            val alleRestanser = repo.hentÅpneBehandlinger()

            alleRestanser.size shouldBe 10

            alleRestanser shouldContainExactlyInAnyOrder listOf(
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.NY_SØKNAD,
                    behandlingStartet = null,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2022),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = søknadsbehandling.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2023),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjent.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2024),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttestering.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                // Vi hopper over 1 saksnummer siden den blir lagret som en del når vi lager en revurdering gjennom
                // hjelpe funksjoner
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2026),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetRevurdering.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2027),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringRevurdering.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2028),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.REVURDERING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjentRevurdering.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2030),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KLAGE,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetKlage.opprettet,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2031),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KLAGE,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = vurdertKlage.opprettet,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2032),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KLAGE,
                    status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = klageTilAttestering.opprettet,
                    periode = null,
                    sakType = Sakstype.UFØRE,
                ),
            )
        }
    }
}
