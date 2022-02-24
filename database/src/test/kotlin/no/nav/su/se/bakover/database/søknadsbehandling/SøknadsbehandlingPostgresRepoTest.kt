package no.nav.su.se.bakover.database.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avslåttBeregning
import no.nav.su.se.bakover.database.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.innvilgetBeregning
import no.nav.su.se.bakover.database.iverksattAttestering
import no.nav.su.se.bakover.database.persistertVariant
import no.nav.su.se.bakover.database.saksbehandler
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.stønadsperiode
import no.nav.su.se.bakover.database.underkjentAttestering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingPostgresRepoTest {

    @Test
    fun `hent tidligere attestering ved underkjenning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.nyInnvilgetUnderkjenning().also {
                repo.hentEventuellTidligereAttestering(it.id).also {
                    it shouldBe underkjentAttestering
                }
            }
        }
    }

    @Test
    fun `hent for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.nyAvslåttBeregning().also {
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
            val vilkårsvurdert = testDataHelper.nySøknadsbehandling()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `lagring av vilkårsvurdert behandling påvirker ikke andre behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattAvslagMedBeregning()
            testDataHelper.nyInnvilgetVilkårsvurdering()

            dataSource.withSession { session ->
                "select count(1) from behandling where status = :status ".let {
                    it.antall(
                        mapOf("status" to BehandlingsStatus.VILKÅRSVURDERT_INNVILGET.toString()),
                        session,
                    ) shouldBe 1
                    it.antall(mapOf("status" to BehandlingsStatus.IVERKSATT_AVSLAG.toString()), session) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.nyInnvilgetVilkårsvurdering()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.nyAvslåttVilkårsvurdering()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.nySøknadsbehandling(
                stønadsperiode = null,
            )
            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe null
            }

            repo.lagre(vilkårsvurdert.copy(stønadsperiode = stønadsperiode))

            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe stønadsperiode
            }
        }
    }

    @Test
    fun `oppdaterer status og behandlingsinformasjon og sletter beregning og simulering hvis de eksisterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val innvilgetVilkårsvurdering = testDataHelper.nyInnvilgetVilkårsvurdering().also {
                val behandlingId = it.id
                repo.hent(behandlingId) shouldBe it
                dataSource.withSession { session ->
                    "select * from behandling where id = :id".hent(mapOf("id" to behandlingId), session) { row ->
                        row.stringOrNull("beregning") shouldBe null
                        row.stringOrNull("simulering") shouldBe null
                    }
                }
            }

            val beregnet = innvilgetVilkårsvurdering
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                ).getOrFail()
                .also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe it.persistertVariant()
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
            val simulert = beregnet.tilSimulert(simulering(beregnet.fnr))
                .also { simulert ->
                    repo.lagre(simulert)
                    repo.hent(simulert.id) shouldBe simulert.persistertVariant()
                    dataSource.withSession {
                        "select * from behandling where id = :id".hent(
                            mapOf("id" to innvilgetVilkårsvurdering.id),
                            it,
                        ) {
                            it.stringOrNull("beregning") shouldNotBe null
                            it.stringOrNull("simulering") shouldNotBe null
                        }
                    }
                }
            // Tilbake til vilkårsvurdert
            simulert.tilVilkårsvurdert(behandlingsinformasjonMedAlleVilkårOppfylt, clock = fixedClock)
                .also { vilkårsvurdert ->
                    repo.lagre(vilkårsvurdert)
                    repo.hent(vilkårsvurdert.id) shouldBe vilkårsvurdert.persistertVariant()
                    dataSource.withSession {
                        "select * from behandling where id = :id".hent(
                            mapOf("id" to innvilgetVilkårsvurdering.id),
                            it,
                        ) {
                            it.stringOrNull("beregning") shouldBe null
                            it.stringOrNull("simulering") shouldBe null
                        }
                    }
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
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.nyTilInnvilgetAttestering()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe Søknadsbehandling.TilAttestering.Innvilget(
                        id = tilAttestering.id,
                        opprettet = tilAttestering.opprettet,
                        sakId = tilAttestering.sakId,
                        saksnummer = tilAttestering.saksnummer,
                        søknad = tilAttestering.søknad,
                        oppgaveId = nyOppgaveId,
                        behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                        fnr = tilAttestering.fnr,
                        beregning = innvilgetBeregning(),
                        simulering = simulering(tilAttestering.fnr),
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = "",
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = tilAttestering.grunnlagsdata,
                        vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                    ).persistertVariant()
                }
            }
        }
    }

    @Test
    fun `til attestering avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
            val tilAttestering = testDataHelper.nyTilAvslåttAttesteringUtenBeregning()
            tilAttestering.nyOppgaveId(nyOppgaveId).also {
                repo.lagre(it)
                repo.hent(it.id).also {
                    it shouldBe Søknadsbehandling.TilAttestering.Avslag.UtenBeregning(
                        id = tilAttestering.id,
                        opprettet = tilAttestering.opprettet,
                        sakId = tilAttestering.sakId,
                        saksnummer = tilAttestering.saksnummer,
                        søknad = tilAttestering.søknad,
                        oppgaveId = nyOppgaveId,
                        behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                        fnr = tilAttestering.fnr,
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = "",
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = tilAttestering.grunnlagsdata,
                        vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående),
                    )
                }
            }
        }
    }

    @Test
    fun `til attestering avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
            val tilAttestering = testDataHelper.tilAvslåttAttesteringMedBeregning()
            tilAttestering.nyOppgaveId(nyOppgaveId).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
                    id = tilAttestering.id,
                    opprettet = tilAttestering.opprettet,
                    sakId = tilAttestering.sakId,
                    saksnummer = tilAttestering.saksnummer,
                    søknad = tilAttestering.søknad,
                    oppgaveId = nyOppgaveId,
                    behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                    fnr = tilAttestering.fnr,
                    beregning = avslåttBeregning,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "",
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = tilAttestering.grunnlagsdata,
                    vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                    attesteringer = Attesteringshistorikk.empty(),
                    avkorting = AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående),
                ).persistertVariant()
            }
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.nyInnvilgetUnderkjenning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe Søknadsbehandling.Underkjent.Innvilget(
                        id = tilAttestering.id,
                        opprettet = tilAttestering.opprettet,
                        sakId = tilAttestering.sakId,
                        saksnummer = tilAttestering.saksnummer,
                        søknad = tilAttestering.søknad,
                        oppgaveId = nyOppgaveId,
                        behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                        fnr = tilAttestering.fnr,
                        beregning = innvilgetBeregning(),
                        simulering = simulering(tilAttestering.fnr),
                        saksbehandler = saksbehandler,
                        attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
                        fritekstTilBrev = "",
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = tilAttestering.grunnlagsdata,
                        vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                        avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                    ).persistertVariant()
                }
            }
        }
    }

    @Test
    fun `underkjent avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
            val tilAttestering = testDataHelper.nyUnderkjenningUtenBeregning()
            tilAttestering.nyOppgaveId(nyOppgaveId).also {
                repo.lagre(it)
                repo.hent(it.id).also {
                    it shouldBe Søknadsbehandling.Underkjent.Avslag.UtenBeregning(
                        id = tilAttestering.id,
                        opprettet = tilAttestering.opprettet,
                        sakId = tilAttestering.sakId,
                        saksnummer = tilAttestering.saksnummer,
                        søknad = tilAttestering.søknad,
                        oppgaveId = nyOppgaveId,
                        behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                        fnr = tilAttestering.fnr,
                        saksbehandler = saksbehandler,
                        attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
                        fritekstTilBrev = "",
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = tilAttestering.grunnlagsdata,
                        vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                        avkorting = AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående),
                    )
                }
            }
        }
    }

    @Test
    fun `underkjent avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
            val tilAttestering = testDataHelper.nyUnderkjenningMedBeregning()
            tilAttestering.nyOppgaveId(nyOppgaveId).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe Søknadsbehandling.Underkjent.Avslag.MedBeregning(
                    id = tilAttestering.id,
                    opprettet = tilAttestering.opprettet,
                    sakId = tilAttestering.sakId,
                    saksnummer = tilAttestering.saksnummer,
                    søknad = tilAttestering.søknad,
                    oppgaveId = nyOppgaveId,
                    behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                    fnr = tilAttestering.fnr,
                    beregning = avslåttBeregning,
                    saksbehandler = saksbehandler,
                    attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
                    fritekstTilBrev = "",
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = tilAttestering.grunnlagsdata,
                    vilkårsvurderinger = tilAttestering.vilkårsvurderinger,
                    avkorting = AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående),
                ).persistertVariant()
            }
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt avslag innvilget`() {

            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val iverksatt = testDataHelper.nyIverksattInnvilget().first
                repo.hent(iverksatt.id) shouldBe iverksatt.persistertVariant()
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.nyIverksattAvslagUtenBeregning(fritekstTilBrev = "Dette er fritekst")
            val expected = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = iverksatt.id,
                opprettet = iverksatt.opprettet,
                sakId = iverksatt.sakId,
                saksnummer = iverksatt.saksnummer,
                søknad = iverksatt.søknad,
                oppgaveId = iverksatt.oppgaveId,
                behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                fnr = iverksatt.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(iverksattAttestering),
                fritekstTilBrev = "Dette er fritekst",
                stønadsperiode = stønadsperiode,
                grunnlagsdata = iverksatt.grunnlagsdata,
                vilkårsvurderinger = iverksatt.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
                ),
            )
            repo.hent(iverksatt.id).also {
                it shouldBe expected
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.nyIverksattAvslagMedBeregning()
            repo.hent(iverksatt.id) shouldBe Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                id = iverksatt.id,
                opprettet = iverksatt.opprettet,
                sakId = iverksatt.sakId,
                saksnummer = iverksatt.saksnummer,
                søknad = iverksatt.søknad,
                oppgaveId = iverksatt.oppgaveId,
                behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                fnr = iverksatt.fnr,
                beregning = avslåttBeregning.toSnapshot(),
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(iverksattAttestering),
                fritekstTilBrev = "",
                stønadsperiode = stønadsperiode,
                grunnlagsdata = iverksatt.grunnlagsdata,
                vilkårsvurderinger = iverksatt.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
                ),
            )
        }
    }

    @Test
    fun `iverksatt avslag med beregning med grunnlag og vilkårsvurderinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.nyIverksattAvslagMedBeregning()

            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            )
            dataSource.withTransaction { tx ->
                testDataHelper.uføreVilkårsvurderingRepo.lagre(iverksatt.id, vurderingUførhet, tx)
            }

            repo.hent(iverksatt.id).also {
                it shouldBe Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                    id = iverksatt.id,
                    opprettet = iverksatt.opprettet,
                    sakId = iverksatt.sakId,
                    saksnummer = iverksatt.saksnummer,
                    søknad = iverksatt.søknad,
                    oppgaveId = iverksatt.oppgaveId,
                    behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                    fnr = iverksatt.fnr,
                    beregning = avslåttBeregning.toSnapshot(),
                    saksbehandler = saksbehandler,
                    attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(iverksattAttestering),
                    fritekstTilBrev = "",
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = iverksatt.grunnlagsdata,
                    vilkårsvurderinger = iverksatt.vilkårsvurderinger.copy(
                        uføre = vurderingUførhet,
                    ),
                    avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                        håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
                    ),
                )
            }
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            val nySak: NySak = testDataHelper.nySakMedNySøknad()
            søknadsbehandlingRepo.hentForSøknad(nySak.søknad.id) shouldBe null
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val søknad = sak.journalførtSøknadMedOppgave()
            testDataHelper.nySøknadsbehandling(sak, søknad).let {
                søknadsbehandlingRepo.hentForSøknad(søknad.id) shouldBe it
            }
        }
    }

    @Test
    fun `kan lagre og hente avslag manglende dokumentasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opprettet = testDataHelper.nyInnvilgetVilkårsvurdering()

            testDataHelper.søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(
                avslag = AvslagManglendeDokumentasjon.tryCreate(
                    søknadsbehandling = opprettet,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "hshshshs",
                    clock = fixedClock,
                ).getOrFail("Feil i oppsett"),
            )

            testDataHelper.søknadsbehandlingRepo.hent(opprettet.id) shouldBe Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = opprettet.id,
                opprettet = opprettet.opprettet,
                sakId = opprettet.sakId,
                saksnummer = opprettet.saksnummer,
                søknad = opprettet.søknad,
                oppgaveId = opprettet.oppgaveId,
                behandlingsinformasjon = opprettet.behandlingsinformasjon,
                fnr = opprettet.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.create(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant(saksbehandler.navIdent),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
                fritekstTilBrev = "hshshshs",
                stønadsperiode = Stønadsperiode.create(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.desember(2021),
                    ),
                    begrunnelse = "",
                ),
                grunnlagsdata = opprettet.grunnlagsdata,
                vilkårsvurderinger = opprettet.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
                ),
            )
        }
    }

    @Test
    fun `gjør ingenting med avkorting dersom ingenting har blitt avkortet`() {
        withMigratedDb { dataSource ->
            val iverksattInnvilgetUtenAvkorting = søknadsbehandlingIverksattInnvilget().second
            val iverksattAvslagMedBeregning = søknadsbehandlingIverksattAvslagMedBeregning().second
            val iverksattAvslagUtenBeregning = søknadsbehandlingIverksattAvslagUtenBeregning().second

            val avkortingsvarselRepoMock = mock<AvkortingsvarselPostgresRepo>()

            val sessionFactory = PostgresSessionFactory(dataSource)

            val repo = SøknadsbehandlingPostgresRepo(
                dataSource = mock(),
                dbMetrics = mock(),
                sessionFactory = PostgresSessionFactory(dataSource),
                avkortingsvarselRepo = avkortingsvarselRepoMock,
                grunnlagsdataOgVilkårsvurderingerPostgresRepo = mock(),
                clock = mock(),
            )

            repo.lagre(
                søknadsbehandling = iverksattInnvilgetUtenAvkorting,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattInnvilgetUtenAvkorting.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående

            repo.lagre(
                søknadsbehandling = iverksattAvslagMedBeregning,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattAvslagMedBeregning.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
            )

            repo.lagre(
                søknadsbehandling = iverksattAvslagUtenBeregning,
                sessionContext = sessionFactory.newTransactionContext(),
            )
            iverksattAvslagUtenBeregning.avkorting shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
            )

            verifyNoInteractions(avkortingsvarselRepoMock)
        }
    }

    @Test
    fun `oppdaterer avkorting ved lagring av iverksatt innvilget søknadsbehandling med avkorting`() {
        withMigratedDb { dataSource ->
            val avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = UUID.randomUUID(),
                        revurderingId = UUID.randomUUID(),
                        simulering = simuleringFeilutbetaling(juni(2021)),
                    ),
                ),
            ).håndter().iverksett(UUID.randomUUID())

            val iverksattInnvilgetAvkortet = søknadsbehandlingIverksattInnvilget().let { (_, iverksatt) ->
                iverksatt.copy(avkorting = avkorting)
            }

            val avkortingsvarselRepoMock = mock<AvkortingsvarselPostgresRepo>()

            val sessionFactory = PostgresSessionFactory(dataSource)

            val repo = SøknadsbehandlingPostgresRepo(
                dataSource = mock(),
                dbMetrics = mock(),
                sessionFactory = PostgresSessionFactory(dataSource),
                avkortingsvarselRepo = avkortingsvarselRepoMock,
                grunnlagsdataOgVilkårsvurderingerPostgresRepo = mock(),
                clock = mock(),
            )

            repo.lagre(
                søknadsbehandling = iverksattInnvilgetAvkortet,
                sessionContext = sessionFactory.newTransactionContext(),
            )

            verify(avkortingsvarselRepoMock).lagre(
                avkortingsvarsel = argThat<Avkortingsvarsel.Utenlandsopphold.Avkortet> { it shouldBe avkorting.avkortingsvarsel },
                tx = anyOrNull(),
            )
            verifyNoMoreInteractions(avkortingsvarselRepoMock)
        }
    }
}
