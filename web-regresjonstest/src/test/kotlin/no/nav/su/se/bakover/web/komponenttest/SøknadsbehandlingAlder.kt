package no.nav.su.se.bakover.web.komponenttest

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fnrOver67
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.VilkårsvurderRequest
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.utilstrekkeligDokumentert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SøknadsbehandlingAlder {
    @Test
    fun `sanity check`() {
        withKomptestApplication() { appComponents ->
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
                it.message shouldBe "Kan ikke legge til uførevilkår for vilkårsvurdering alder"
            }

            appComponents.services.søknadsbehandling.vilkårsvurder(
                request = VilkårsvurderRequest(
                    behandlingId = søknadsbehandling.id,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
                ),
            ).getOrFail()

            appComponents.services.søknadsbehandling.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = tilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                ),
            ).getOrFail()

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
                oppdatert.vilkårsvurderinger.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                    vilkår = setOf(
                        Vilkår.Formue.IkkeVurdert,
                        UtenlandsoppholdVilkår.IkkeVurdert,
                    ),
                )

                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag shouldBe emptyList()
                    it.bosituasjon.single().shouldBeType<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                }
            }

            appComponents.services.søknadsbehandling.vilkårsvurder(
                request = VilkårsvurderRequest(
                    behandlingId = søknadsbehandling.id,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                ),
            ).getOrFail()

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
                oppdatert.vilkårsvurderinger.resultat.shouldBeType<Vilkårsvurderingsresultat.Innvilget>()

                oppdatert.grunnlagsdata.also {
                    it.fradragsgrunnlag.single().also {
                        it.månedsbeløp shouldBe 1000.0
                        it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                    }
                    it.bosituasjon.single().shouldBeType<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                }
            }

            assertThrows<NotImplementedError> {
                appComponents.services.søknadsbehandling.beregn(
                    request = SøknadsbehandlingService.BeregnRequest(
                        behandlingId = søknadsbehandling.id,
                        begrunnelse = null,
                    ),
                )
            }.also {
                it.message shouldContain "vilkårsvurdering_alder Beregning av alder er ikke implementert enda"
            }

            appComponents.services.søknadsbehandling.leggTilOpplysningspliktVilkår(
                request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                    behandlingId = søknadsbehandling.id,
                    vilkår = utilstrekkeligDokumentert(periode = stønadsperiode2022.periode),
                ),
            ).getOrFail().also {
                it.vilkårsvurderinger.resultat.shouldBeType<Vilkårsvurderingsresultat.Avslag>()
            }

            val avslag = appComponents.services.søknadsbehandling.hent(
                request = SøknadsbehandlingService.HentRequest(behandlingId = søknadsbehandling.id),
            ).getOrFail().also { oppdatert ->
                oppdatert.vilkårsvurderinger.resultat.shouldBeType<Vilkårsvurderingsresultat.Avslag>()
            }

            assertThrows<NotImplementedError> {
                appComponents.services.søknadsbehandling.brev(
                    request = SøknadsbehandlingService.BrevRequest.MedFritekst(
                        behandling = avslag,
                        fritekst = "",
                    ),
                )
            }.also {
                it.message shouldContain "vilkårsvurdering_alder brev for alder ikke implementert enda"
            }
        }
    }
}
