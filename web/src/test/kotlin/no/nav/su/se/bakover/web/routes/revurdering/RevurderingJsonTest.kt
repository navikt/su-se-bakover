package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.bosituasjon
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.expectedBosituasjonJson
import no.nav.su.se.bakover.web.routes.grunnlag.FradragsgrunnlagJsonTest.Companion.expectedFradragsgrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.FradragsgrunnlagJsonTest.Companion.fradragsgrunnlag
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.expectedVurderingUføreJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.vurderingsperiodeUføre
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.TestBeregning
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.vedtak.toJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toJson
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class RevurderingJsonTest {
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )
    private val vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling =
        RevurderingRoutesTestData.vedtak

    @Test
    fun `should serialize and deserialize OpprettetRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt

        val revurdering = OpprettetRevurdering(
            id = id,
            periode = år(2021),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(fradragsgrunnlag),
                bosituasjon = listOf(bosituasjon),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
                ),
                formue = formuevilkårIkkeVurdert(),
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "status": "${RevurderingsStatus.OPPRETTET}",
                "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-12-31"
                },
                "saksbehandler": "Petter",
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": $expectedVurderingUføreJson,
                  "fradrag": [$expectedFradragsgrunnlagJson],
                  "bosituasjon": $expectedBosituasjonJson,
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<OpprettetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize BeregnetInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Innvilget(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "status": "${RevurderingsStatus.BEREGNET_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                            "gyldigFra":"2022-05-01",
                            "beløp":55739
                      },
                      {
                          "gyldigFra": "2021-05-01",
                          "beløp": 53200
                      },
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }                     
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(
            revurderingJson,
            serialize(revurdering.toJson(satsFactoryTestPåDato(20.mai(2022)))),
            true,
        )
        deserialize<BeregnetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(
            satsFactoryTestPåDato(20.mai(2022)),
        )
    }

    @Test
    fun `should serialize and deserialize BeregnetOpphørt`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Opphørt(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "status": "${RevurderingsStatus.BEREGNET_OPPHØRT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<BeregnetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize BeregnetIngenEndring`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.IngenEndring(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "status": "${RevurderingsStatus.BEREGNET_INGEN_ENDRING}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt":null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<BeregnetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize SimulertInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = SimulertRevurdering.Innvilget(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeAvgjort(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sakId,
                revurderingId = id,
                periode = beregning.periode,
            ),
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.SIMULERT_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": {
                  "avgjørelse": "IKKE_AVGJORT"
                }
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<SimulertRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize SimulertOpphørt`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = SimulertRevurdering.Opphørt(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.SIMULERT_OPPHØRT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<SimulertRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize InnvilgetTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.Vurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.TIL_ATTESTERING_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt":null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "Vurdert",
                  "Inntekt": "Vurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize OpphørtTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering.Opphørt(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.TIL_ATTESTERING_OPPHØRT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize IngenEndringTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering.IngenEndring(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": null,
                "status": "${RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": false,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize UnderkjentInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = UnderkjentRevurdering.Innvilget(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                Attestering.Underkjent(
                    attestant = NavIdentBruker.Attestant("attestant"),
                    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
                    kommentar = "Dokumentasjon mangler",
                    opprettet = attesteringOpprettet,
                ),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        )

        val expected =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.UNDERKJENT_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "attesteringer": [{
                    "attestant": "attestant",
                    "opprettet": "$attesteringOpprettet",
                    "underkjennelse": {
                        "grunn": "DOKUMENTASJON_MANGLER",
                        "kommentar": "Dokumentasjon mangler"
                    }
                }],
                "sakstype": "UFØRE",
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL"},
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<UnderkjentRevurderingJson>(expected) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize UnderkjentOpphør`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = UnderkjentRevurdering.Opphørt(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                Attestering.Underkjent(
                    attestant = NavIdentBruker.Attestant("attestant"),
                    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
                    kommentar = "Dokumentasjon mangler",
                    opprettet = attesteringOpprettet,
                ),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
        )

        val expected =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning":${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.UNDERKJENT_OPPHØRT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "attesteringer": [{
                    "attestant": "attestant",
                    "opprettet": "$attesteringOpprettet",
                    "underkjennelse": {
                        "grunn": "DOKUMENTASJON_MANGLER",
                        "kommentar": "Dokumentasjon mangler"
                    }
                }],
                "sakstype": "UFØRE",
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL"},
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<UnderkjentRevurderingJson>(expected) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize UnderkjentIngenEndring`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = UnderkjentRevurdering.IngenEndring(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("OppgaveId"),
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                Attestering.Underkjent(
                    attestant = NavIdentBruker.Attestant("attestant"),
                    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
                    kommentar = "Dokumentasjon mangler",
                    opprettet = attesteringOpprettet,
                ),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val expected =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": null,
                "status": "${RevurderingsStatus.UNDERKJENT_INGEN_ENDRING}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "attesteringer": [{
                    "attestant": "attestant",
                    "opprettet": "$attesteringOpprettet",
                    "underkjennelse": {
                        "grunn": "DOKUMENTASJON_MANGLER",
                        "kommentar": "Dokumentasjon mangler"
                    }
                }],
                "sakstype": "UFØRE",
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": false,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<UnderkjentRevurderingJson>(expected) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize IverksattInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = IverksattRevurdering.Innvilget(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("OppgaveId"),
            beregning = beregning,
            simulering = mock(),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(
                    Attestering.Iverksatt(
                        NavIdentBruker.Attestant("attestant"),
                        attesteringOpprettet,
                    ),
                ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = AvventerKravgrunnlag(
                avgjort = Tilbakekrev(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    sakId = sakId,
                    revurderingId = id,
                    periode = beregning.periode,
                ),
            ),
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.IVERKSATT_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type":  "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                  "resultat": null,
                    "formuegrenser": [
                     
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [{"attestant":"attestant", "opprettet": "$attesteringOpprettet", "underkjennelse": null}],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": {
                  "avgjørelse": "TILBAKEKREV"
                }
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<IverksattRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize IverksattOpphørt`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = IverksattRevurdering.Opphørt(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("OppgaveId"),
            beregning = beregning,
            simulering = mock(),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(
                    Attestering.Iverksatt(
                        NavIdentBruker.Attestant("attestant"),
                        attesteringOpprettet,
                    ),
                ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": {
                  "perioder": [],
                  "totalBruttoYtelse": 0
                },
                "status": "${RevurderingsStatus.IVERKSATT_OPPHØRT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": { "type":  "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [{"attestant": "attestant", "opprettet": "$attesteringOpprettet", "underkjennelse": null}],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<IverksattRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `should serialize and deserialize IverksattIngenEndring`() {
        val id = UUID.randomUUID()
        val opprettet = fixedTidspunkt
        val beregning = TestBeregning
        val attesteringOpprettet = fixedTidspunkt

        val revurdering = IverksattRevurdering.IngenEndring(
            id = id,
            periode = år(2020),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("OppgaveId"),
            beregning = beregning,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(
                    Attestering.Iverksatt(
                        NavIdentBruker.Attestant("attestant"),
                        attesteringOpprettet,
                    ),
                ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilUtsendingAvVedtaksbrev = true,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
        )

        val revurderingJson =
            //language=JSON
            """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregning": ${serialize(beregning.toJson())},
                "simulering": null,
                "status": "${RevurderingsStatus.IVERKSATT_INGEN_ENDRING}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "fritekstTilBrev": "",
                "skalFøreTilBrevutsending": true,
                "årsak": "MELDING_FRA_BRUKER",
                "begrunnelse": "Ny informasjon",
                "forhåndsvarsel": null,
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": null,
                  "fradrag": [],
                  "bosituasjon": [],
                  "formue": {
                    "resultat": null,
                    "formuegrenser": [
                      {
                          "gyldigFra": "2020-05-01",
                          "beløp": 50676
                      }
                    ],
                    "vurderinger": []
                  },
                  "utenlandsopphold": null,
                  "opplysningsplikt": null,
                  "pensjon": null
                },
                "informasjonSomRevurderes": {
                  "Uførhet": "IkkeVurdert",
                  "Inntekt": "IkkeVurdert"
                },
                "attesteringer": [{"attestant": "attestant", "opprettet": "$attesteringOpprettet", "underkjennelse": null}],
                "sakstype": "UFØRE",
                "simuleringForAvkortingsvarsel": null,
                "tilbakekrevingsbehandling": null
            }
            """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson(satsFactoryTestPåDato())), true)
        deserialize<IverksattRevurderingJson>(revurderingJson) shouldBe revurdering.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun `serialiserer revurdering for stans av ytelse`() {
        val simulertRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
            .second

        val simulertRevurderingJson =
            //language=JSON
            """
            {
                "id": "${simulertRevurdering.id}",
                "opprettet": "${simulertRevurdering.opprettet}",
                "tilRevurdering": ${serialize(simulertRevurdering.tilRevurdering.toJson())},
                "simulering": ${serialize(simulertRevurdering.simulering.toJson())},
                "status": "${RevurderingsStatus.SIMULERT_STANS}",
                "saksbehandler": "saksbehandler",
                "periode": {
                    "fraOgMed": "2021-02-01",
                    "tilOgMed": "2021-12-31"
                },
                "årsak": "${Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING}",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "begrunnelse": "valid",
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": ${serialize((simulertRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail() as Vilkår.Uførhet.Vurdert).toJson())},
                  "fradrag": [],
                  "bosituasjon": ${serialize(simulertRevurdering.grunnlagsdata.bosituasjon.toJson())},
                  "formue": ${serialize(simulertRevurdering.vilkårsvurderinger.formue.toJson(satsFactoryTestPåDato()))},
                  "utenlandsopphold": ${serialize(simulertRevurdering.vilkårsvurderinger.utenlandsopphold.toJson()!!)},
                  "opplysningsplikt": ${serialize(simulertRevurdering.vilkårsvurderinger.opplysningsplikt.toJson()!!)},
                  "pensjon": null
                },
                "attesteringer": [],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(
            simulertRevurderingJson,
            serialize(simulertRevurdering.toJson(satsFactoryTestPåDato())), true,
        )

        val iverksattRevurdering = simulertRevurdering.iverksett(
            attestering = attesteringIverksatt(clock = fixedClock),
        ).getOrFail("Feil med oppsett av testdata")

        val iverksattRevurderingJson =
            //language=JSON
            """
            {
                "id": "${iverksattRevurdering.id}",
                "opprettet": "${iverksattRevurdering.opprettet}",
                "tilRevurdering": ${serialize(iverksattRevurdering.tilRevurdering.toJson())},
                "simulering": ${serialize(iverksattRevurdering.simulering.toJson())},
                "status": "${RevurderingsStatus.IVERKSATT_STANS}",
                "saksbehandler": "saksbehandler",
                "periode": {
                    "fraOgMed": "2021-02-01",
                    "tilOgMed": "2021-12-31"
                },
                "årsak": "${Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING}",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "begrunnelse": "valid",
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": ${serialize((iverksattRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail() as Vilkår.Uførhet.Vurdert).toJson())},
                  "fradrag": [],
                  "bosituasjon": ${serialize(iverksattRevurdering.grunnlagsdata.bosituasjon.toJson())},
                  "formue": ${serialize(iverksattRevurdering.vilkårsvurderinger.formue.toJson(satsFactoryTestPåDato()))},
                  "utenlandsopphold": ${serialize(iverksattRevurdering.vilkårsvurderinger.utenlandsopphold.toJson()!!)},
                  "opplysningsplikt": ${serialize(simulertRevurdering.vilkårsvurderinger.opplysningsplikt.toJson()!!)},
                  "pensjon": null
                },
                "attesteringer": [{"attestant": "attestant", "opprettet": "$fixedTidspunkt", "underkjennelse": null}],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(
            iverksattRevurderingJson,
            serialize(iverksattRevurdering.toJson(satsFactoryTestPåDato())), true,
        )
    }

    @Test
    fun `serialiserer revurdering for gjenopptak av ytelse`() {
        val simulertRevurdering = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()
            .second

        val simulertRevurderingJson =
            //language=JSON
            """
            {
                "id": "${simulertRevurdering.id}",
                "opprettet": "${simulertRevurdering.opprettet}",
                "tilRevurdering": ${serialize(simulertRevurdering.tilRevurdering.toJson())},
                "simulering": ${serialize(simulertRevurdering.simulering.toJson())},
                "status": "${RevurderingsStatus.SIMULERT_GJENOPPTAK}",
                "saksbehandler": "saksbehandler",
                "periode": {
                    "fraOgMed": "2021-02-01",
                    "tilOgMed": "2021-12-31"
                },
                "årsak": "${Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING}",
                "begrunnelse": "valid",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": ${serialize((simulertRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail() as Vilkår.Uførhet.Vurdert).toJson())},
                  "fradrag": [],
                  "bosituasjon": ${serialize(simulertRevurdering.grunnlagsdata.bosituasjon.toJson())},
                  "formue": ${serialize(simulertRevurdering.vilkårsvurderinger.formue.toJson(satsFactoryTestPåDato()))},
                  "utenlandsopphold": ${serialize(simulertRevurdering.vilkårsvurderinger.utenlandsopphold.toJson()!!)},
                  "opplysningsplikt": ${serialize(simulertRevurdering.vilkårsvurderinger.opplysningsplikt.toJson()!!)},
                  "pensjon": null
                },
                "attesteringer": [],
                "sakstype": "UFØRE"

            }
            """.trimIndent()

        JSONAssert.assertEquals(
            simulertRevurderingJson,
            serialize(simulertRevurdering.toJson(satsFactoryTestPåDato())), true,
        )

        val iverksattRevurdering = simulertRevurdering.iverksett(attesteringIverksatt(clock = fixedClock))
            .getOrFail("Feil i oppsett av testdata")

        val iverksattRevurderingJson =
            //language=JSON
            """
            {
                "id": "${iverksattRevurdering.id}",
                "opprettet": "${iverksattRevurdering.opprettet}",
                "tilRevurdering": ${serialize(iverksattRevurdering.tilRevurdering.toJson())},
                "simulering": ${serialize(iverksattRevurdering.simulering.toJson())},
                "status": "${RevurderingsStatus.IVERKSATT_GJENOPPTAK}",
                "saksbehandler": "saksbehandler",
                "periode": {
                    "fraOgMed": "2021-02-01",
                    "tilOgMed": "2021-12-31"
                },
                "årsak": ${Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING},
                "begrunnelse": "valid",
                "forhåndsvarsel": { "type": "INGEN_FORHÅNDSVARSEL" },
                "grunnlagsdataOgVilkårsvurderinger": {
                  "uføre": ${serialize((iverksattRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail() as Vilkår.Uførhet.Vurdert).toJson())},
                  "fradrag": [],
                  "bosituasjon": ${serialize(iverksattRevurdering.grunnlagsdata.bosituasjon.toJson())},
                  "formue": ${serialize(iverksattRevurdering.vilkårsvurderinger.formue.toJson(satsFactoryTestPåDato()))},
                  "utenlandsopphold": ${serialize(iverksattRevurdering.vilkårsvurderinger.utenlandsopphold.toJson()!!)},
                  "opplysningsplikt": ${serialize(iverksattRevurdering.vilkårsvurderinger.opplysningsplikt.toJson()!!)},
                  "pensjon": null
                },
                "attesteringer": [{"attestant": "attestant", "opprettet": "$fixedTidspunkt", "underkjennelse": null}],
                "sakstype": "UFØRE"
            }
            """.trimIndent()

        JSONAssert.assertEquals(
            iverksattRevurderingJson,
            serialize(iverksattRevurdering.toJson(satsFactoryTestPåDato())), true,
        )
    }

    @Test
    fun `mapping av instans til status`() {
        InstansTilStatusMapper(mock<OpprettetRevurdering>()).status shouldBe RevurderingsStatus.OPPRETTET
        InstansTilStatusMapper(mock<BeregnetRevurdering.IngenEndring>()).status shouldBe RevurderingsStatus.BEREGNET_INGEN_ENDRING
        InstansTilStatusMapper(mock<BeregnetRevurdering.Innvilget>()).status shouldBe RevurderingsStatus.BEREGNET_INNVILGET
        InstansTilStatusMapper(mock<BeregnetRevurdering.Opphørt>()).status shouldBe RevurderingsStatus.BEREGNET_OPPHØRT
        InstansTilStatusMapper(mock<SimulertRevurdering.Innvilget>()).status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
        InstansTilStatusMapper(mock<SimulertRevurdering.Opphørt>()).status shouldBe RevurderingsStatus.SIMULERT_OPPHØRT
        InstansTilStatusMapper(mock<RevurderingTilAttestering.IngenEndring>()).status shouldBe RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING
        InstansTilStatusMapper(mock<RevurderingTilAttestering.Innvilget>()).status shouldBe RevurderingsStatus.TIL_ATTESTERING_INNVILGET
        InstansTilStatusMapper(mock<RevurderingTilAttestering.Opphørt>()).status shouldBe RevurderingsStatus.TIL_ATTESTERING_OPPHØRT
        InstansTilStatusMapper(mock<IverksattRevurdering.IngenEndring>()).status shouldBe RevurderingsStatus.IVERKSATT_INGEN_ENDRING
        InstansTilStatusMapper(mock<IverksattRevurdering.Innvilget>()).status shouldBe RevurderingsStatus.IVERKSATT_INNVILGET
        InstansTilStatusMapper(mock<IverksattRevurdering.Opphørt>()).status shouldBe RevurderingsStatus.IVERKSATT_OPPHØRT
        InstansTilStatusMapper(mock<UnderkjentRevurdering.IngenEndring>()).status shouldBe RevurderingsStatus.UNDERKJENT_INGEN_ENDRING
        InstansTilStatusMapper(mock<UnderkjentRevurdering.Innvilget>()).status shouldBe RevurderingsStatus.UNDERKJENT_INNVILGET
        InstansTilStatusMapper(mock<UnderkjentRevurdering.Opphørt>()).status shouldBe RevurderingsStatus.UNDERKJENT_OPPHØRT
        InstansTilStatusMapper(mock<StansAvYtelseRevurdering.SimulertStansAvYtelse>()).status shouldBe RevurderingsStatus.SIMULERT_STANS
        InstansTilStatusMapper(mock<StansAvYtelseRevurdering.IverksattStansAvYtelse>()).status shouldBe RevurderingsStatus.IVERKSATT_STANS
        InstansTilStatusMapper(mock<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>()).status shouldBe RevurderingsStatus.SIMULERT_GJENOPPTAK
        InstansTilStatusMapper(mock<GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse>()).status shouldBe RevurderingsStatus.IVERKSATT_GJENOPPTAK
    }
}
