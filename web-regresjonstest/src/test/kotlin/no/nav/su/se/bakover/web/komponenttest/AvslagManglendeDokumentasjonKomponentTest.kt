package no.nav.su.se.bakover.web.komponenttest

import arrow.core.nonEmptyListOf
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.vilkår.uføreVilkår
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
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilStønadsperiode
import org.junit.jupiter.api.Test
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.Vurdering
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import vilkår.uføre.domain.UføreVilkår
import vilkår.vurderinger.domain.Grunnlagsdata
import java.time.LocalDate
import java.util.UUID

class AvslagManglendeDokumentasjonKomponentTest {
    @Test
    fun `kan avslå pga manglende dokumentasjon for søknader uten behandling`() {
        withKomptestApplication { appComponents ->
            val søknadJson = nyDigitalSøknad(Fnr.generer().toString(), this.client)
            val søknadId = UUID.fromString(NySøknadJson.Response.hentSøknadId(søknadJson))

            appComponents.services.avslåSøknadManglendeDokumentasjonService.avslå(
                AvslåManglendeDokumentasjonCommand(
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    fritekstTilBrev = "Du må svare i telefonen",
                ),
            )

            val expectedPeriode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).startOfMonth(),
                tilOgMed = LocalDate.now(fixedClock).endOfMonth(),
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.hentForSøknad(søknadId)!!.let { søknadsbehandling ->
                søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>().let { avslag ->
                    avslag.søknad.id shouldBe søknadId
                    avslag.periode shouldBe expectedPeriode
                    avslag.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                    avslag.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
                    avslag.avslagsgrunner shouldBe listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                    avslag.aldersvurdering.shouldBeType<Aldersvurdering.SkalIkkeVurderes>()
                    avslag.vilkårsvurderinger.opplysningspliktVilkår().shouldBeType<OpplysningspliktVilkår.Vurdert>()
                        .let { actualVilkår ->
                            avslag.vilkårsvurderinger shouldBe VilkårsvurderingerSøknadsbehandling.Uføre.ikkeVurdert().copy(
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
                    avslag.vilkårsvurderinger.resultat() shouldBe Vurdering.Avslag
                    appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSak(avslag.sakId))
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
            val søknadJson = nyDigitalSøknad(Fnr.generer().toString(), client = this.client)
            val sakId = NySøknadJson.Response.hentSakId(søknadJson)
            val søknadId = NySøknadJson.Response.hentSøknadId(søknadJson)

            val søknadsbehandlingJson = nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
                client = this.client,
            )
            val behandlingId = BehandlingJson.hentBehandlingId(søknadsbehandlingJson)
            val fraOgMed = 1.mai(2021)
            val tilOgMed = 30.april(2022)

            leggTilStønadsperiode(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed.toString(),
                tilOgMed = tilOgMed.toString(),
                client = this.client,
            )
            leggTilUføregrunnlag(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed.toString(),
                tilOgMed = tilOgMed.toString(),
                client = this.client,
            )

            appComponents.services.avslåSøknadManglendeDokumentasjonService.avslå(
                AvslåManglendeDokumentasjonCommand(
                    søknadId = UUID.fromString(søknadId),
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    fritekstTilBrev = "Du må svare i telefonen",
                ),
            )

            val expectedPeriode = Periode.create(
                fraOgMed = fraOgMed.startOfMonth(),
                tilOgMed = tilOgMed.endOfMonth(),
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.hentForSøknad(UUID.fromString(søknadId))!!
                .let { søknadsbehandling ->
                    søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>().let { avslag ->
                        avslag.periode shouldBe expectedPeriode
                        avslag.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                        avslag.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
                        avslag.avslagsgrunner shouldBe listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                        avslag.aldersvurdering.shouldBeType<Aldersvurdering.Vurdert>()
                        avslag.vilkårsvurderinger.opplysningspliktVilkår()
                            .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                            .let { actualVilkår ->
                                avslag.vilkårsvurderinger shouldBe avslag.vilkårsvurderinger.oppdaterVilkår(
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
                        avslag.vilkårsvurderinger.resultat() shouldBe Vurdering.Avslag
                        appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSak(avslag.sakId))
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
