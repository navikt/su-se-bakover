package no.nav.su.se.bakover.web.komponenttest

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.service.søknad.AvslåManglendeDokumentasjonRequest
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class AvslagManglendeDokumentasjonKomponentTest {
    @Test
    fun `kan avslå pga manglende dokumentasjon for søknader uten behandling`() {
        withKomptestApplication { appComponents ->
            val søknadJson = nyDigitalSøknad(Fnr.generer().toString())
            val søknadId = UUID.fromString(NySøknadJson.Response.hentSøknadId(søknadJson))

            appComponents.services.avslåSøknadManglendeDokumentasjonService.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    fritekstTilBrev = "Du må svare i telefonen",
                ),
            )

            val expectedPeriode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).startOfMonth(),
                tilOgMed = LocalDate.now(fixedClock).endOfMonth(),
            )

            appComponents.services.søknadsbehandling.hentForSøknad(søknadId)!!.let { søknadsbehandling ->
                søknadsbehandling.shouldBeType<Søknadsbehandling.Iverksatt.Avslag.UtenBeregning>().let { avslag ->
                    avslag.søknad.id shouldBe søknadId
                    avslag.periode shouldBe expectedPeriode
                    avslag.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                    avslag.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
                    avslag.avslagsgrunner shouldBe listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                    avslag.vilkårsvurderinger.opplysningspliktVilkår().shouldBeType<OpplysningspliktVilkår.Vurdert>()
                        .let { actualVilkår ->
                            avslag.vilkårsvurderinger shouldBe Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert().copy(
                                opplysningsplikt = OpplysningspliktVilkår.Vurdert.tryCreate(
                                    vurderingsperioder = nonEmptyListOf(
                                        VurderingsperiodeOpplysningsplikt.create(
                                            id = actualVilkår.vurderingsperioder.single().id,
                                            opprettet = fixedTidspunkt,
                                            periode = expectedPeriode,
                                            grunnlag = Opplysningspliktgrunnlag(
                                                id = UUID.randomUUID(),
                                                opprettet = fixedTidspunkt,
                                                periode = expectedPeriode,
                                                beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                            ),
                                        ),
                                    ),
                                ).getOrFail(),
                            )
                        }
                    avslag.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Avslag(
                        setOf(avslag.vilkårsvurderinger.opplysningspliktVilkår()),
                    )
                    appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Sak(avslag.sakId))
                        .let { dokumenter ->
                            dokumenter.single().let {
                                it.tittel shouldBe "Avslag supplerende stønad"
                                it.generertDokumentJson shouldContain """"avslagsgrunner": ["MANGLENDE_DOKUMENTASJON"]"""
                            }
                        }
                }
            }
        }
    }

    @Test
    fun `kan avslå pga manglende dokumentasjon for søknader med påbegynt behandling`() {
        withKomptestApplication { appComponents ->
            val søknadJson = nyDigitalSøknad(Fnr.generer().toString())
            val sakId = NySøknadJson.Response.hentSakId(søknadJson)
            val søknadId = NySøknadJson.Response.hentSøknadId(søknadJson)

            val søknadsbehandlingJson = nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
            )
            val behandlingId = BehandlingJson.hentBehandlingId(søknadsbehandlingJson)
            val fraOgMed = 1.mai(2021)
            val tilOgMed = 30.april(2022)

            leggTilVirkningstidspunkt(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed.toString(),
                tilOgMed = tilOgMed.toString(),
            )
            leggTilUføregrunnlag(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed.toString(),
                tilOgMed = tilOgMed.toString(),
            )

            appComponents.services.avslåSøknadManglendeDokumentasjonService.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId = UUID.fromString(søknadId),
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    fritekstTilBrev = "Du må svare i telefonen",
                ),
            )

            val expectedPeriode = Periode.create(
                fraOgMed = fraOgMed.startOfMonth(),
                tilOgMed = tilOgMed.endOfMonth(),
            )

            appComponents.services.søknadsbehandling.hentForSøknad(UUID.fromString(søknadId))!!
                .let { søknadsbehandling ->
                    søknadsbehandling.shouldBeType<Søknadsbehandling.Iverksatt.Avslag.UtenBeregning>().let { avslag ->
                        avslag.periode shouldBe expectedPeriode
                        avslag.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                        avslag.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
                        avslag.avslagsgrunner shouldBe listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                        avslag.vilkårsvurderinger.opplysningspliktVilkår()
                            .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                            .let { actualVilkår ->
                                avslag.vilkårsvurderinger shouldBe avslag.vilkårsvurderinger.leggTil(
                                    OpplysningspliktVilkår.Vurdert.tryCreate(
                                        vurderingsperioder = nonEmptyListOf(
                                            VurderingsperiodeOpplysningsplikt.create(
                                                id = actualVilkår.vurderingsperioder.single().id,
                                                opprettet = fixedTidspunkt,
                                                periode = expectedPeriode,
                                                grunnlag = Opplysningspliktgrunnlag(
                                                    id = UUID.randomUUID(),
                                                    opprettet = fixedTidspunkt,
                                                    periode = expectedPeriode,
                                                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                                ),
                                            ),
                                        ),
                                    ).getOrFail(),
                                )
                                avslag.vilkårsvurderinger.uføreVilkår().getOrFail().shouldBeType<UføreVilkår.Vurdert>()
                            }
                        avslag.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Avslag(
                            setOf(avslag.vilkårsvurderinger.opplysningspliktVilkår()),
                        )
                        appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Sak(avslag.sakId))
                            .let { dokumenter ->
                                dokumenter.single().let {
                                    it.tittel shouldBe "Avslag supplerende stønad"
                                    it.generertDokumentJson shouldContain """"avslagsgrunner": ["MANGLENDE_DOKUMENTASJON"]"""
                                }
                            }
                    }
                }
        }
    }
}
