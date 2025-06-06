package no.nav.su.se.bakover.web.komponenttest

import arrow.core.nonEmptyListOf
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonCommand
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonerCommand
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVurderinger
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.web.TestClientsBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import satser.domain.Satskategori
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Vurdering
import vilkår.formue.domain.Formueverdier
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.personligOppmøtevilkårInnvilget
import vilkår.uføre.domain.Uføregrad

internal class SøknadsbehandlingAlderKomponentTest {

    @Test
    fun `skal kunne sende inn alderssøknad`() {
        withKomptestApplication(
            clientsBuilder = { databaseRepos, clock, _applicationConfig ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                    personOppslag = PersonOppslagStub(fødselsdato = 1.januar(1954)),
                ).build(_applicationConfig)
            },
            clock = 17.juni(2022).fixedClock(),
        ) { appComponents ->

            appComponents.services.søknad.nySøknad(
                søknadInnhold = søknadsinnholdAlder(),
                identBruker = saksbehandler,
            )

            val sak = appComponents.services.sak.hentSak(fnrOver67, Sakstype.ALDER).getOrFail()
            val søknad = sak.søknader.single()

            søknad.type shouldBe Sakstype.ALDER

            val (_, søknadsbehandling) = appComponents.services.søknadsbehandling.søknadsbehandlingService.opprett(
                request = SøknadsbehandlingService.OpprettRequest(
                    søknadId = søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.søknadsbehandlingService.oppdaterStønadsperiode(
                request = SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = søknadsbehandling.id,
                    stønadsperiode = stønadsperiode2022,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    saksbehandlersAvgjørelse = null,
                ),
            ).getOrFail()

            assertThrows<IllegalArgumentException> {
                appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilUførevilkår(
                    request = LeggTilUførevurderingerRequest(
                        behandlingId = søknadsbehandling.id,
                        vurderinger = nonEmptyListOf(
                            LeggTilUførevilkårRequest(
                                behandlingId = søknadsbehandling.id,
                                periode = stønadsperiode2022.periode,
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 0,
                                oppfylt = UførevilkårStatus.VilkårOppfylt,
                                begrunnelse = "dette bør ikke gå",
                            ),
                        ),
                    ),
                    saksbehandler = saksbehandler,
                )
            }.also {
                it.message shouldBe "Kan ikke legge til uførevilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)"
            }
            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = tilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilPensjonsVilkår(
                request = LeggTilPensjonsVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = pensjonsVilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
                saksbehandler = saksbehandler,
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = søknadsbehandling.id,
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(
                            stønadsperiode2022.periode,
                            FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilLovligOpphold(
                request = LeggTilLovligOppholdRequest(
                    behandlingId = søknadsbehandling.id,
                    vurderinger = listOf(
                        LovligOppholdVurderinger(
                            stønadsperiode2022.periode,
                            LovligOppholdVilkårStatus.VilkårOppfylt,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            )
            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilPersonligOppmøteVilkår(
                request = LeggTilPersonligOppmøteVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = personligOppmøtevilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
                saksbehandler = saksbehandler,
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilFastOppholdINorgeVilkår(
                request = LeggTilFastOppholdINorgeRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = fastOppholdVilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
                saksbehandler = saksbehandler,
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilInstitusjonsoppholdVilkår(
                request = LeggTilInstitusjonsoppholdVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = institusjonsoppholdvilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
                saksbehandler = saksbehandler,
            )

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilBosituasjongrunnlag(
                request = LeggTilBosituasjonerCommand(
                    behandlingId = søknadsbehandling.id,
                    bosituasjoner = listOf(
                        LeggTilBosituasjonCommand(
                            periode = stønadsperiode2022.periode,
                            epsFnr = null,
                            delerBolig = false,
                            ektemakeEllerSamboerUførFlyktning = null,
                            epsFylt67 = null,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail()

            appComponents.services.søknadsbehandling.søknadsbehandlingService.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.opplysningspliktVilkår()
                oppdatert.vilkårsvurderinger.resultat() shouldBe Vurdering.Uavklart
                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag shouldBe emptyList()
                    it.bosituasjon.single().shouldBeType<Bosituasjon.Fullstendig.Enslig>()
                }
            }
            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilFormuevilkår(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                            periode = stønadsperiode2022.periode,
                            epsFormue = null,
                            søkersFormue = Formueverdier.empty(),
                            begrunnelse = null,
                            måInnhenteMerInformasjon = false,
                        ),
                    ),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
            )
            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilUtenlandsopphold(
                LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = søknadsbehandling.id,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = søknadsbehandling.id.value,
                            periode = stønadsperiode2022.periode,
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail()

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilFradragsgrunnlag(
                request = LeggTilFradragsgrunnlagRequest(
                    behandlingId = søknadsbehandling.id,
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt1000(
                            periode = stønadsperiode2022.periode,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail()

            appComponents.services.søknadsbehandling.søknadsbehandlingService.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.resultat().shouldBeType<Vurdering.Innvilget>()

                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag.single().also {
                        it.månedsbeløp shouldBe 1000.0
                        it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                    }
                    it.bosituasjon.single().shouldBeType<Bosituasjon.Fullstendig.Enslig>()
                }
            }

            appComponents.services.søknadsbehandling.søknadsbehandlingService.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = søknadsbehandling.id,
                    begrunnelse = null,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().also {
                it.beregning.getSumYtelse() shouldBe 195188
                it.beregning.getSumFradrag() shouldBe 12000
                it.beregning.getMånedsberegninger().all { it.getSats() == Satskategori.HØY } shouldBe true
                it.beregning.getMånedsberegninger().count { it.getSatsbeløp() == 16868.75 } shouldBe 4
                it.beregning.getMånedsberegninger().count { it.getSatsbeløp() == 17464.25 } shouldBe 8
                it.beregning.getMånedsberegninger() shouldHaveSize 12
            }

            appComponents.services.søknadsbehandling.søknadsbehandlingService.simuler(
                request = SøknadsbehandlingService.SimulerRequest(
                    behandlingId = søknadsbehandling.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().also {
                it.simulering.hentTotalUtbetaling().sum() shouldBe 195188
                it.simulering.hentTilUtbetaling().sum() shouldBe 195188
            }

            appComponents.services.søknadsbehandling.søknadsbehandlingService.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = utilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().also {
                it.vilkårsvurderinger.resultat().shouldBeType<Vurdering.Avslag>()
            }

            appComponents.services.søknadsbehandling.søknadsbehandlingService.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.resultat().shouldBeType<Vurdering.Avslag>()
            }
        }
    }
}
