package no.nav.su.se.bakover.web.komponenttest

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fnrOver67
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVurderinger
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.personligOppmøtevilkårInnvilget

internal class SøknadsbehandlingAlder {
    @Test
    fun `sanity check`() {
        withKomptestApplication(
            clock = 17.juni(2022).fixedClock(),
        ) { appComponents ->
            appComponents.services.søknad.nySøknad(
                søknadInnhold = søknadsinnholdAlder(),
                identBruker = saksbehandler,
            )

            val sak = appComponents.services.sak.hentSak(fnrOver67, Sakstype.ALDER).getOrFail()
            val søknad = sak.søknader.single()

            søknad.type shouldBe Sakstype.ALDER

            val søknadsbehandling = appComponents.services.søknadsbehandling.opprett(
                request = SøknadsbehandlingService.OpprettRequest(søknadId = søknad.id),
            ).getOrFail()

            appComponents.services.søknadsbehandling.oppdaterStønadsperiode(
                request = SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = søknadsbehandling.id,
                    stønadsperiode = stønadsperiode2022,
                    sakId = sak.id,
                ),
            ).getOrFail()

            assertThrows<IllegalArgumentException> {
                appComponents.services.søknadsbehandling.leggTilUførevilkår(
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
                )
            }.also {
                it.message shouldBe "Kan ikke legge til uførevilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)"
            }
            appComponents.services.søknadsbehandling.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = tilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.leggTilPensjonsVilkår(
                request = LeggTilPensjonsVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = pensjonsVilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
            )

            appComponents.services.søknadsbehandling.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = søknadsbehandling.id,
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(FamiliegjenforeningvilkårStatus.VilkårOppfylt),
                    ),
                ),
            )

            appComponents.services.søknadsbehandling.leggTilLovligOpphold(
                request = LeggTilLovligOppholdRequest(
                    behandlingId = søknadsbehandling.id,
                    vurderinger = listOf(
                        LovligOppholdVurderinger(stønadsperiode2022.periode, LovligOppholdVilkårStatus.VilkårOppfylt),
                    ),
                ),
            )
            appComponents.services.søknadsbehandling.leggTilPersonligOppmøteVilkår(
                request = LeggTilPersonligOppmøteVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = personligOppmøtevilkårInnvilget(periode = stønadsperiode2022.periode)
                )
            )

            appComponents.services.søknadsbehandling.leggTilFastOppholdINorgeVilkår(
                request = LeggTilFastOppholdINorgeRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = fastOppholdVilkårInnvilget(periode = stønadsperiode2022.periode)
                )
            )

            appComponents.services.søknadsbehandling.leggTilInstitusjonsoppholdVilkår(
                request = LeggTilInstitusjonsoppholdVilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    vilkår = institusjonsoppholdvilkårInnvilget(periode = stønadsperiode2022.periode),
                ),
            )

            appComponents.services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(
                request = LeggTilBosituasjonEpsRequest(
                    behandlingId = søknadsbehandling.id,
                    epsFnr = null,
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.fullførBosituasjongrunnlag(
                request = FullførBosituasjonRequest(
                    behandlingId = søknadsbehandling.id,
                    bosituasjon = BosituasjonValg.BOR_ALENE,
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.opplysningspliktVilkår()
                oppdatert.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(
                    vilkår = setOf(
                        FormueVilkår.IkkeVurdert,
                        UtenlandsoppholdVilkår.IkkeVurdert,
                    ),
                )

                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag shouldBe emptyList()
                    it.bosituasjon.single().shouldBeType<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                }
            }
            appComponents.services.søknadsbehandling.leggTilFormuevilkår(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = søknadsbehandling.id,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                            periode = stønadsperiode2022.periode,
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.create(
                                verdiIkkePrimærbolig = 0,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 0,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            begrunnelse = null,
                            måInnhenteMerInformasjon = false,
                        ),
                    ),
                ),
            )
            appComponents.services.søknadsbehandling.leggTilUtenlandsopphold(
                request = LeggTilUtenlandsoppholdRequest(
                    behandlingId = søknadsbehandling.id,
                    periode = stønadsperiode2022.periode,
                    status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                    begrunnelse = null,
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.leggTilFradragsgrunnlag(
                request = LeggTilFradragsgrunnlagRequest(
                    behandlingId = søknadsbehandling.id,
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt1000(
                            periode = stønadsperiode2022.periode,
                        ),
                    ),
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.vurdering.shouldBeType<Vilkårsvurderingsresultat.Innvilget>()

                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag.single().also {
                        it.månedsbeløp shouldBe 1000.0
                        it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                    }
                    it.bosituasjon.single().shouldBeType<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                }
            }

            appComponents.services.søknadsbehandling.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = søknadsbehandling.id,
                    begrunnelse = null,
                ),
            ).getOrFail().also {
                it.beregning.getSumYtelse() shouldBe 195188
                it.beregning.getSumFradrag() shouldBe 12000
                it.beregning.getMånedsberegninger().all { it.getSats() == Satskategori.HØY } shouldBe true
                it.beregning.getMånedsberegninger().count { it.getSatsbeløp() == 16868.75 } shouldBe 4
                it.beregning.getMånedsberegninger().count { it.getSatsbeløp() == 17464.25 } shouldBe 8
                it.beregning.getMånedsberegninger() shouldHaveSize 12
            }

            appComponents.services.søknadsbehandling.simuler(
                request = SøknadsbehandlingService.SimulerRequest(
                    behandlingId = søknadsbehandling.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().also {
                it.simulering.bruttoYtelse() shouldBe 195188
                it.simulering.tolk().simulertePerioder.all { it.utbetalinger.all { it is TolketUtbetaling.Ordinær } }
            }

            appComponents.services.søknadsbehandling.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = utilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                ),
            ).getOrFail().also {
                it.vilkårsvurderinger.vurdering.shouldBeType<Vilkårsvurderingsresultat.Avslag>()
            }

            appComponents.services.søknadsbehandling.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.vurdering.shouldBeType<Vilkårsvurderingsresultat.Avslag>()
            }
        }
    }
}
