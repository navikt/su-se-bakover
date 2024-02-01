package no.nav.su.se.bakover.database.søknadsbehandling

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.infrastructure.persistence.antall
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.database.skatt.SkattPostgresRepo
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.toFormueRequestGrunnlag
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt

internal class GrunnlagsdataOgVilkårsvurderingerSøknadsbehandlingPostgresRepoTest {

    @Test
    fun `hent for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisterSøknadsbehandlingBeregnetAvslag().second.also {
                testDataHelper.sessionFactory.withSessionContext { session ->
                    repo.hentForSak(it.sakId, session)
                }
            }
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Uavklart>()
            }
        }
    }

    @Test
    fun `lagring av vilkårsvurdert behandling påvirker ikke andre behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()

            dataSource.withSession { session ->
                "select count(1) from behandling where status = :status ".let {
                    it.antall(
                        mapOf("status" to SøknadsbehandlingStatusDB.VILKÅRSVURDERT_INNVILGET.toString()),
                        session,
                    ) shouldBe 1
                    it.antall(
                        mapOf("status" to SøknadsbehandlingStatusDB.IVERKSATT_AVSLAG.toString()),
                        session,
                    ) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vilkårsvurdert: VilkårsvurdertSøknadsbehandling.Innvilget =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            val repo = testDataHelper.søknadsbehandlingRepo
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertAvslag().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo

            val (sak, vilkårsvurdert) = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode { (sak, søknad) ->
                nySøknadsbehandlingMedStønadsperiode(
                    sakOgSøknad = sak to søknad,
                    stønadsperiode = Stønadsperiode.create(periode = januar(2021)),
                ).let {
                    it.first to it.second
                }
            }

            repo.lagre(
                sak.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = vilkårsvurdert.id,
                    stønadsperiode = stønadsperiode2021,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(fixedLocalDate),
                    clock = fixedClock,
                    saksbehandler = saksbehandler,
                    hentPerson = { person().right() },
                    saksbehandlersAvgjørelse = null,
                ).getOrFail().second,
            )

            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe stønadsperiode2021
            }
        }
    }

    @Test
    fun `oppdaterer status og vilkår og sletter beregning og simulering hvis de eksisterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val (sak, innvilgetVilkårsvurdering) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()
                .also { (_, innvilget) ->
                    repo.hent(innvilget.id) shouldBe innvilget
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(mapOf("id" to innvilget.id), session) { row ->
                            row.stringOrNull("beregning") shouldBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }

            val beregnet = innvilgetVilkårsvurdering
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                    nySaksbehandler = saksbehandler,
                ).getOrFail()
                .also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe it
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(
                            mapOf("id" to innvilgetVilkårsvurdering.id),
                            session,
                        ) { row ->
                            row.stringOrNull("beregning") shouldNotBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }
            beregnet as BeregnetSøknadsbehandling.Innvilget
            val simulert = beregnet.simuler(
                saksbehandler = saksbehandler,
                clock = fixedClock,
            ) { _, _ ->
                simulerUtbetaling(
                    sak = sak,
                    søknadsbehandling = beregnet,
                    strict = false,
                ).map {
                    it.simulering
                }
            }.getOrFail().also { simulert ->
                repo.lagre(simulert)
                repo.hent(simulert.id) shouldBe simulert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldNotBe null
                        row.stringOrNull("simulering") shouldNotBe null
                    }
                }
            }

            // Tilbake til vilkårsvurdert
            simulert.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = simulert.id,
                    formuegrunnlag = simulert.vilkårsvurderinger.formue.grunnlag.toFormueRequestGrunnlag(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).getOrFail().also { vilkårsvurdert ->
                repo.lagre(vilkårsvurdert)
                repo.hent(vilkårsvurdert.id) shouldBe vilkårsvurdert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldBe null
                        row.stringOrNull("simulering") shouldBe null
                    }
                }
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `persistert og hentet beregning er lik - selvom behandlingen er startet før satsendring`() {
            withMigratedDb { dataSource ->
                val klokkeFørBeregning = TikkendeKlokke(fixedClockAt(1.mai(2021)))
                val klokkeUnderBeregning = TikkendeKlokke(fixedClockAt(1.juni(2021)))
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingBeregnetInnvilget(

                    sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(),
                    søknadsbehandling = { (sak, søknad) ->
                        beregnetSøknadsbehandling(
                            clock = klokkeFørBeregning,
                            beregnetClock = klokkeUnderBeregning,
                            sakOgSøknad = sak to søknad,
                        )
                    },
                ).second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `til attestering innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `til attestering avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `til attestering avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `underkjent avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `underkjent avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().second
                repo.hent(iverksatt.id) shouldBe iverksatt
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val (_, iverksatt) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandling(
                    sakOgSøknad = sak to søknad,
                    fritekstTilBrev = "Dette er fritekst",
                    customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
                    attestering = attesteringIverksatt(clock = enUkeEtterFixedClock),
                )
            }
            repo.hent(iverksatt.id).also {
                it shouldBe iverksatt
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `iverksatt avslag med beregning med grunnlag og vilkårsvurderinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            val nySak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            søknadsbehandlingRepo.hentForSøknad(nySak.søknad.id) shouldBe null
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisternySøknadsbehandlingMedStønadsperiode().second.let {
                søknadsbehandlingRepo.hentForSøknad(it.søknad.id) shouldBe it
            }
        }
    }

    @Test
    fun `kan lagre og hente avslag manglende dokumentasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opprettetMedStønadsperiode =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning().second

            testDataHelper.søknadsbehandlingRepo.lagre(
                søknadsbehandling = opprettetMedStønadsperiode,
            )

            testDataHelper.søknadsbehandlingRepo.hent(opprettetMedStønadsperiode.id) shouldBe opprettetMedStønadsperiode
            opprettetMedStønadsperiode.shouldBeInstanceOf<IverksattSøknadsbehandling.Avslag.UtenBeregning>()
        }
    }

    @Test
    fun `lagrer & erstatter & fjerner med skattegrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val behandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget(
                eksterneGrunnlag = eksternGrunnlagHentet().copy(
                    skatt = EksterneGrunnlagSkatt.IkkeHentet,
                ),
            ).second

            repo.hent(behandling.id).let {
                it shouldNotBe null
                it!!.eksterneGrunnlag.skatt shouldBe EksterneGrunnlagSkatt.IkkeHentet
            }

            val behandlingMedSkatt = behandling.leggTilSkatt(
                EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(), null),
            ).getOrFail().also { medSkatt ->
                repo.lagre(medSkatt)
                repo.hent(medSkatt.id) shouldBe medSkatt
            }

            val oppdatertMedEps = behandlingMedSkatt.leggTilSkatt(
                EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(), nySkattegrunnlag()),
            ).getOrFail().also { medEps ->
                repo.lagre(medEps)
                repo.hent(medEps.id) shouldBe medEps
            }

            oppdatertMedEps.leggTilSkatt(
                EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(), null),
            ).getOrFail().also { utenEps ->
                repo.lagre(utenEps)
                repo.hent(utenEps.id) shouldBe utenEps
            }
        }
    }

    @Test
    fun `fjerner skattemelding ved lagring av behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val medEps = testDataHelper.persisternySøknadsbehandlingMedStønadsperiodeMedSkatt(
                EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(), nySkattegrunnlag()),
            )
            val skattRepo = SkattPostgresRepo
            val medEpsSøkersId = (medEps.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id
            val medEpsEpsId = (medEps.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).eps!!.id

            repo.hent(medEps.id)!!.let {
                it.eksterneGrunnlag.skatt.shouldBeInstanceOf<EksterneGrunnlagSkatt.Hentet>()
                val casted = (it.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet)
                casted.søkers.id shouldBe medEpsSøkersId
                casted.eps?.id shouldNotBe null
                casted.eps?.id shouldBe medEpsEpsId
            }

            dataSource.withSession { session ->
                skattRepo.hent(medEpsSøkersId, session) shouldNotBe null
                skattRepo.hent(medEpsEpsId, session) shouldNotBe null
            }

            val utenEps = medEps.copy(
                grunnlagsdataOgVilkårsvurderinger = medEps.grunnlagsdataOgVilkårsvurderinger.copy(
                    eksterneGrunnlag = medEps.eksterneGrunnlag.fjernEps(),
                ),
            ).also { utenEps ->
                repo.lagre(utenEps)

                repo.hent(utenEps.id)!!.let {
                    it.eksterneGrunnlag.skatt.shouldBeInstanceOf<EksterneGrunnlagSkatt.Hentet>()
                    val casted = (it.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet)
                    casted.eps shouldBe null
                }
            }

            val utenEpsEpsId = (utenEps.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).eps?.id

            dataSource.withSession { session ->
                skattRepo.hent(medEpsSøkersId, session) shouldNotBe null

                skattRepo.hent(medEpsEpsId, session) shouldBe null
                medEpsEpsId shouldNotBe utenEpsEpsId
            }
        }
    }
}
