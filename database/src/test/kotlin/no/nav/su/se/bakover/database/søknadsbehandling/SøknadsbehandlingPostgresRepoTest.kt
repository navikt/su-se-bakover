package no.nav.su.se.bakover.database.søknadsbehandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.avslåttBeregning
import no.nav.su.se.bakover.database.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.database.beregning
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.iverksattAttestering
import no.nav.su.se.bakover.database.saksbehandler
import no.nav.su.se.bakover.database.simulering
import no.nav.su.se.bakover.database.underkjentAttestering
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SøknadsbehandlingPostgresRepoTest {

    private val dataSource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(dataSource)
    private val repo = SøknadsbehandlingPostgresRepo(dataSource)

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

    private fun iverksattInnvilget(fom: LocalDate, tom: LocalDate) =
        testDataHelper.nyIverksattInnvilget(periode = Periode.create(fom, tom))

    @Test
    fun `opprett og hent behandling for utbetaling`() {
        withMigratedDb {
            val nySøknadsbehandling = testDataHelper.nyIverksattInnvilget()
            val hentet = repo.hentBehandlingForUtbetaling(nySøknadsbehandling.second.id)!!
            hentet shouldBe nySøknadsbehandling.first
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb {
            val vilkårsvurdert = testDataHelper.nyUavklartVilkårsvurdering()
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
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
    fun `oppdaterer status og behandlingsinformasjon og sletter beregning og simulering hvis de eksisterer`() {

        withMigratedDb {
            val uavklartVilkårsvurdering = testDataHelper.nyUavklartVilkårsvurdering().also {
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
                beregning = beregning()
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
                simulering(beregnet.fnr)
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
                behandlingsinformasjonMedAlleVilkårOppfylt
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
                            fritekstTilBrev = ""
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
                            fritekstTilBrev = ""
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
                            fritekstTilBrev = ""
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
                )
            }
        }
    }
}
