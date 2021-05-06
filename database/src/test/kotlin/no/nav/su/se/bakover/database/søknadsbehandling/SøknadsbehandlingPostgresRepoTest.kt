package no.nav.su.se.bakover.database.søknadsbehandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.avslåttBeregning
import no.nav.su.se.bakover.database.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.database.beregning
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.grunnlag.GrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.iverksattAttestering
import no.nav.su.se.bakover.database.saksbehandler
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.stønadsperiode
import no.nav.su.se.bakover.database.underkjentAttestering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingPostgresRepoTest {

    private val dataSource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(dataSource)
    private val uføregrunnlagPostgresRepo = UføregrunnlagPostgresRepo(dataSource)
    private val grunnlagRepo = GrunnlagPostgresRepo(
        uføregrunnlagRepo = uføregrunnlagPostgresRepo,
    )
    private val vilkårsvurderingRepo = VilkårsvurderingPostgresRepo(dataSource, uføregrunnlagPostgresRepo)
    private val repo = SøknadsbehandlingPostgresRepo(dataSource, grunnlagRepo, vilkårsvurderingRepo)

    @Test
    fun `hent tidligere attestering ved underkjenning`() {
        withMigratedDb {
            testDataHelper.nyInnvilgetUnderkjenning().also {
                repo.hentEventuellTidligereAttestering(it.id).also {
                    it shouldBe underkjentAttestering
                }
            }
        }
    }

    @Test
    fun `hent for sak`() {
        withMigratedDb {
            testDataHelper.nyAvslåttBeregning().also {
                dataSource.withSession { session ->
                    repo.hentForSak(it.sakId, session)
                }
            }
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb {
            val vilkårsvurdert = testDataHelper.nySøknadsbehandling()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `lagring av vilkårsvurdert behandling påvirker ikke andre behandlinger`() {
        withMigratedDb {
            testDataHelper.nyIverksattAvslagMedBeregning()
            testDataHelper.nyInnvilgetVilkårsvurdering()

            dataSource.withSession { session ->
                "select count(1) from behandling where status = :status ".let {
                    it.antall(mapOf("status" to BehandlingsStatus.VILKÅRSVURDERT_INNVILGET.toString()), session) shouldBe 1
                    it.antall(mapOf("status" to BehandlingsStatus.IVERKSATT_AVSLAG.toString()), session) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb {
            val vilkårsvurdert = testDataHelper.nyInnvilgetVilkårsvurdering()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb {
            val vilkårsvurdert = testDataHelper.nyAvslåttVilkårsvurdering()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb {
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

        withMigratedDb {
            val uavklartVilkårsvurdering = testDataHelper.nySøknadsbehandling().also {
                val behandlingId = it.id
                repo.hent(behandlingId) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to behandlingId), it) {
                        it.stringOrNull("beregning") shouldBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
            val beregnet = uavklartVilkårsvurdering.tilBeregnet(
                beregning = beregning(),
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to uavklartVilkårsvurdering.id), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
            val simulert = beregnet.tilSimulert(
                simulering(beregnet.fnr),
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to uavklartVilkårsvurdering.id), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldNotBe null
                    }
                }
            }
            // Tilbake til vilkårsvurdert
            simulert.tilVilkårsvurdert(
                behandlingsinformasjonMedAlleVilkårOppfylt,
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to uavklartVilkårsvurdering.id), it) {
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
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.nyTilInnvilgetAttestering()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id).also {
                        it shouldBe Søknadsbehandling.TilAttestering.Innvilget(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = beregning(),
                            simulering = simulering(tilAttestering.fnr),
                            saksbehandler = saksbehandler,
                            fritekstTilBrev = "",
                            stønadsperiode = stønadsperiode,
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }

        @Test
        fun `til attestering avslag uten beregning`() {
            withMigratedDb {
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
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }

        @Test
        fun `til attestering avslag med beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.tilAvslåttAttesteringMedBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id).also {
                        it shouldBe Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
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
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.nyInnvilgetUnderkjenning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id).also {
                        it shouldBe Søknadsbehandling.Underkjent.Innvilget(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = beregning(),
                            simulering = simulering(tilAttestering.fnr),
                            saksbehandler = saksbehandler,
                            attestering = underkjentAttestering,
                            fritekstTilBrev = "",
                            stønadsperiode = stønadsperiode,
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag uten beregning`() {
            withMigratedDb {
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
                            attestering = underkjentAttestering,
                            fritekstTilBrev = "",
                            stønadsperiode = stønadsperiode,
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag med beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = testDataHelper.nyUnderkjenningMedBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(it.id).also {
                        it shouldBe Søknadsbehandling.Underkjent.Avslag.MedBeregning(
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
                            attestering = underkjentAttestering,
                            fritekstTilBrev = "",
                            stønadsperiode = stønadsperiode,
                            grunnlagsdata = Grunnlagsdata.EMPTY,
                            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                        )
                    }
                }
            }
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt avslag innvilget`() {

            withMigratedDb {
                val iverksatt = testDataHelper.nyIverksattInnvilget().first
                repo.hent(iverksatt.id).also {
                    it shouldBe iverksatt
                }
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {

        withMigratedDb {
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
                attestering = iverksattAttestering,
                fritekstTilBrev = "Dette er fritekst",
                stønadsperiode = stønadsperiode,
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            )
            repo.hent(iverksatt.id).also {
                it shouldBe expected
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb {
            val iverksatt = testDataHelper.nyIverksattAvslagMedBeregning()
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
                    beregning = avslåttBeregning,
                    saksbehandler = saksbehandler,
                    attestering = iverksattAttestering,
                    fritekstTilBrev = "",
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = Grunnlagsdata.EMPTY,
                    vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
                )
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning med grunnlag og vilkårsvurderinger`() {
        withMigratedDb {
            val iverksatt = testDataHelper.nyIverksattAvslagMedBeregning()

            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = Vilkår.Vurdert.Uførhet(
                vurderingsperioder = listOf(
                    Vurderingsperiode.Manuell(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            )

            testDataHelper.vilkårsvurderingRepo.lagre(iverksatt.id, vurderingUførhet)

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
                    beregning = avslåttBeregning,
                    saksbehandler = saksbehandler,
                    attestering = iverksattAttestering,
                    fritekstTilBrev = "",
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = Grunnlagsdata(
                        uføregrunnlag = listOf(
                            Grunnlag.Uføregrunnlag(
                                id = uføregrunnlag.id,
                                opprettet = Tidspunkt.now(fixedClock),
                                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 12000,
                            ),
                        ),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger(
                        uføre = Vilkår.Vurdert.Uførhet(
                            vurderingsperioder = listOf(
                                Vurderingsperiode.Manuell(
                                    id = vurderingUførhet.vurderingsperioder[0].id,
                                    opprettet = Tidspunkt.now(fixedClock),
                                    resultat = Resultat.Avslag,
                                    grunnlag = uføregrunnlag,
                                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                                    begrunnelse = "fåkke lov",
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
