package no.nav.su.se.bakover.database.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.søknadsbehandling.AldersvurderingJson.Companion.toDBJson
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersinformasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Year

internal class AldersvurderingJsonTest {

    @Nested
    inner class DomainTilJson {
        @Test
        fun `historisk mappes riktig`() {
            Aldersvurdering.Historisk(stønadsperiode2021).toDBJson() shouldBe AldersvurderingJson(
                vurdering = MaskinellVurdering.HISTORISK,
                fødselsdato = null,
                fødselsår = null,
                alder = null,
                alderSøkerFyllerIÅr = null,
                alderPåTidspunkt = null,
                saksbehandlerTattEnAvgjørelse = false,
                avgjørelsesTidspunkt = null,
            ).let { serialize(it) }
        }

        @Test
        fun `skal_ikke_vurderes mappes riktig`() {
            Aldersvurdering.SkalIkkeVurderes(stønadsperiode2021).toDBJson() shouldBe AldersvurderingJson(
                vurdering = MaskinellVurdering.SKAL_IKKE_VURDERES,
                fødselsdato = null,
                fødselsår = null,
                alder = null,
                alderSøkerFyllerIÅr = null,
                alderPåTidspunkt = null,
                saksbehandlerTattEnAvgjørelse = false,
                avgjørelsesTidspunkt = null,
            ).let { serialize(it) }
        }

        @Nested
        inner class RettPåUføre {
            @Test
            fun `med fødselsdato mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato(
                        fødselsdato = 1.januar(2000),
                        fødselsår = Year.of(2000),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 21,
                        alderSøkerFyllerIÅr = 21,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.RETT_MED_FØDSELSDATO,
                    fødselsdato = "2000-01-01",
                    fødselsår = 2000,
                    alder = 21,
                    alderSøkerFyllerIÅr = 21,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                ).let { serialize(it) }
            }

            @Test
            fun `med fødselsår mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                        stønadsperiode = stønadsperiode2021,
                        fødselsår = Year.of(2000),
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 21,
                        alderSøkerFyllerIÅr = 21,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.RETT_MED_FØDSELSÅR,
                    fødselsdato = null,
                    fødselsår = 2000,
                    alder = 21,
                    alderSøkerFyllerIÅr = 21,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                ).let { serialize(it) }
            }
        }

        @Nested
        inner class IkkeRettPåUføre {
            @Test
            fun `med fødselsdato mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato(
                        fødselsdato = 1.januar(1950),
                        fødselsår = Year.of(1950),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 71,
                        alderSøkerFyllerIÅr = 71,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.IKKE_RETT_MED_FØDSELSDATO,
                    fødselsdato = "1950-01-01",
                    fødselsår = 1950,
                    alder = 71,
                    alderSøkerFyllerIÅr = 71,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                ).let { serialize(it) }
            }

            @Test
            fun `med fødselsår mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår(
                        stønadsperiode = stønadsperiode2021,
                        fødselsår = Year.of(1950),
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 71,
                        alderSøkerFyllerIÅr = 71,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.IKKE_RETT_MED_FØDSELSÅR,
                    fødselsdato = null,
                    fødselsår = 1950,
                    alder = 71,
                    alderSøkerFyllerIÅr = 71,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                ).let { serialize(it) }
            }
        }

        @Nested
        inner class Ukjent {
            @Test
            fun `med fødselsår mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår(
                        fødselsår = Year.of(1954),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 67,
                        alderSøkerFyllerIÅr = 67,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.UKJENT_MED_FØDSELSÅR,
                    fødselsdato = null,
                    fødselsår = 1954,
                    alder = 67,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = false,
                    avgjørelsesTidspunkt = null,
                ).let { serialize(it) }
            }

            @Test
            fun `uten fødselsår mappes riktig`() {
                Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = SaksbehandlersAvgjørelse.Avgjort(fixedTidspunkt),
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = null,
                        alderSøkerFyllerIÅr = null,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                ).toDBJson() shouldBe AldersvurderingJson(
                    vurdering = MaskinellVurdering.UKJENT_UTEN_FØDSELSÅR,
                    fødselsdato = null,
                    fødselsår = null,
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = fixedTidspunkt,
                    saksbehandlerTattEnAvgjørelse = true,
                    avgjørelsesTidspunkt = fixedTidspunkt,
                ).let { serialize(it) }
            }
        }
    }

    @Nested
    inner class JsonTilDomain {
        @Test
        fun `historisk mappes riktig`() {
            AldersvurderingJson.toAldersvurdering(
                json = """{
                "vurdering": "HISTORISK",
                "fødselsdato": null,
                "fødselsår": null,
                "alder": null,
                "alderSøkerFyllerIÅr": null,
                "alderPåTidspunkt": null,
                "saksbehandlerTattEnAvgjørelse": false,
                "avgjørelsesTidspunkt": null
               }
                """.trimIndent(),
                stønadsperiode = stønadsperiode2021,
            ) shouldBe Aldersvurdering.Historisk(stønadsperiode2021)
        }

        @Test
        fun `skalIkkeVurderes mappes riktig`() {
            AldersvurderingJson.toAldersvurdering(
                json = """{
                "vurdering": "SKAL_IKKE_VURDERES",
                "fødselsdato": null,
                "fødselsår": null,
                "alder": null,
                "alderSøkerFyllerIÅr": null,
                "alderPåTidspunkt": null,
                "saksbehandlerTattEnAvgjørelse": false,
                "avgjørelsesTidspunkt": null
               }
                """.trimIndent(),
                stønadsperiode = stønadsperiode2021,
            ) shouldBe Aldersvurdering.SkalIkkeVurderes(stønadsperiode2021)
        }

        @Nested
        inner class RettPåUføre {
            @Test
            fun `med fødselsdato mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "RETT_MED_FØDSELSDATO",
                        "fødselsdato": "2000-01-01",
                        "fødselsår": 2000,
                        "alder": 21,
                        "alderSøkerFyllerIÅr": 21,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": false,
                        "avgjørelsesTidspunkt": null
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato(
                        fødselsdato = 1.januar(2000),
                        fødselsår = Year.of(2000),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 21,
                        alderSøkerFyllerIÅr = 21,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }

            @Test
            fun `med fødselsår mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "RETT_MED_FØDSELSÅR",
                        "fødselsdato": null,
                        "fødselsår": 2000,
                        "alder": 21,
                        "alderSøkerFyllerIÅr": 21,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": false,
                        "avgjørelsesTidspunkt": null
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                        stønadsperiode = stønadsperiode2021,
                        fødselsår = Year.of(2000),
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 21,
                        alderSøkerFyllerIÅr = 21,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }
        }

        @Nested
        inner class IkkeRettPåUføre {
            @Test
            fun `med fødselsdato mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "IKKE_RETT_MED_FØDSELSDATO",
                        "fødselsdato": "1950-01-01",
                        "fødselsår": 1950,
                        "alder": 71,
                        "alderSøkerFyllerIÅr": 71,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": false,
                        "avgjørelsesTidspunkt": null
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato(
                        fødselsdato = 1.januar(1950),
                        fødselsår = Year.of(1950),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 71,
                        alderSøkerFyllerIÅr = 71,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }

            @Test
            fun `med fødselsår mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "IKKE_RETT_MED_FØDSELSÅR",
                        "fødselsdato": null,
                        "fødselsår": 1950,
                        "alder": 71,
                        "alderSøkerFyllerIÅr": 71,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": false,
                        "avgjørelsesTidspunkt": null
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår(
                        stønadsperiode = stønadsperiode2021,
                        fødselsår = Year.of(1950),
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 71,
                        alderSøkerFyllerIÅr = 71,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }
        }

        @Nested
        inner class Ukjent {
            @Test
            fun `med fødselsår mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "UKJENT_MED_FØDSELSÅR",
                        "fødselsdato": null,
                        "fødselsår": 1954,
                        "alder": 67,
                        "alderSøkerFyllerIÅr": 67,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": false,
                        "avgjørelsesTidspunkt": null
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår(
                        fødselsår = Year.of(1954),
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = null,
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = 67,
                        alderSøkerFyllerIÅr = 67,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }

            @Test
            fun `uten fødselsår mappes riktig`() {
                AldersvurderingJson.toAldersvurdering(
                    //language=json
                    json = """{
                        "vurdering": "UKJENT_UTEN_FØDSELSÅR",
                        "fødselsdato": null,
                        "fødselsår": null,
                        "alder": null,
                        "alderSøkerFyllerIÅr": null,
                        "alderPåTidspunkt": "2021-01-01T01:02:03.456789Z",
                        "saksbehandlerTattEnAvgjørelse": true,
                        "avgjørelsesTidspunkt": "2021-01-01T01:02:03.456789Z"
                    }
                    """.trimIndent(),
                    stønadsperiode = stønadsperiode2021,
                ) shouldBe Aldersvurdering.Vurdert(
                    maskinellVurdering = no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                        stønadsperiode = stønadsperiode2021,
                    ),
                    saksbehandlersAvgjørelse = SaksbehandlersAvgjørelse.Avgjort(fixedTidspunkt),
                    aldersinformasjon = Aldersinformasjon.createFromExisting(
                        alder = null,
                        alderSøkerFyllerIÅr = null,
                        alderPåTidspunkt = fixedTidspunkt,
                    ),
                )
            }
        }
    }
}
