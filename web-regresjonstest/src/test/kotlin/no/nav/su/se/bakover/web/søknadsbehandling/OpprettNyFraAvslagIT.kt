package no.nav.su.se.bakover.web.søknadsbehandling

import common.presentation.beregning.FradragResponseJson
import dokument.domain.Dokumenttilstand
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.vedtak.application.NySøknadCommandOmgjøring
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.MerknadJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.MånedsberegningJson
import no.nav.su.se.bakover.web.routes.vedtak.VedtakTypeJson
import no.nav.su.se.bakover.web.sak.SakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.lukkSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.iverksett.AUTOMATISK_ATTESTANT
import no.nav.su.se.bakover.web.vedtak.VedtakJson
import no.nav.su.se.bakover.web.vedtak.opprettNySøknadsbehandlingFraVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import satser.domain.Satskategori
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID
import javax.sql.DataSource
import no.nav.su.se.bakover.web.routes.vedtak.VedtakJson as VedtakResponsJson

private val stønadsperiode = år(2021)
private const val FORVENTET_INNTEKT = 0.0

private val omgjøringPgaNyeOpplysninger = NySøknadCommandOmgjøring(
    Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK.name,
    Omgjøringsgrunn.NYE_OPPLYSNINGER.name,
)

@ExtendWith(DbExtension::class)
class OpprettNyFraAvslagIT(private val dataSource: DataSource) {

    @Test
    fun `kan opprette ny søknadsbehandling fra et avslagsvedtak (vilkår) - kan ikke opprette dersom søknaden senere blir lukket`() {
        withTestApplicationAndEmbeddedDb(dataSource, clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, søknadId) = opprettAvslåttSøknadsbehandlingPgaVilkår(client = this.client).let {
                Pair(BehandlingJson.hentSakId(it), BehandlingJson.hentSøknadId(it))
            }
            val sak = hentSak(sakId, client = this.client)
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandling
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                VedtakJson.hentVedtakId(it)
            }
            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId,
                vedtakId,
                this.client,
                søknadId,
                postbody = omgjøringPgaNyeOpplysninger,
            )
            appComponents.lukkSøknad(søknadId, this.client)
            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId,
                vedtakId,
                this.client,
                søknadId,
                verifiserResponsVilkårAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
                postbody = omgjøringPgaNyeOpplysninger,
            )
        }
    }

    @Test
    fun `kan opprette ny søknadsbehandling fra et avslagsvedtak (beregning) - kan ikke opprette dersom søknaden senere blir lukket`() {
        withTestApplicationAndEmbeddedDb(dataSource, clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, behandlingId, søknadId) = opprettAvslåttSøknadsbehandlingPgaBeregning(
                fraOgMed = stønadsperiode.fraOgMed.toString(),
                tilOgMed = stønadsperiode.tilOgMed.toString(),
                client = this.client,
            ).let {
                Triple(
                    BehandlingJson.hentSakId(it),
                    BehandlingJson.hentBehandlingId(it),
                    BehandlingJson.hentSøknadId(it),
                )
            }
            val sak = hentSak(sakId, client = this.client)
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandlingPgaBeregning
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                verifiserVedtak(it, behandlingId)
                VedtakJson.hentVedtakId(it)
            }

            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                verifiserResponsBeregningAvslag = true,
                postbody = omgjøringPgaNyeOpplysninger,
            )
            appComponents.lukkSøknad(søknadId, this.client)
            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                verifiserResponsBeregningAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
                postbody = omgjøringPgaNyeOpplysninger,
            )
        }
    }

    @Test
    fun `kan ikke opprette ny behandling dersom det finnes en åpen behandling fra før`() {
        withTestApplicationAndEmbeddedDb(dataSource, clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, behandlingId, søknadId) = opprettAvslåttSøknadsbehandlingPgaBeregning(
                fraOgMed = stønadsperiode.fraOgMed.toString(),
                tilOgMed = stønadsperiode.tilOgMed.toString(),
                client = this.client,
            ).let {
                Triple(
                    BehandlingJson.hentSakId(it),
                    BehandlingJson.hentBehandlingId(it),
                    BehandlingJson.hentSøknadId(it),
                )
            }
            val (sak, fnr) = hentSak(sakId, client = this.client).let {
                it to SakJson.hentFnr(it)
            }
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandlingPgaBeregning
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                verifiserVedtak(it, behandlingId)
                VedtakJson.hentVedtakId(it)
            }

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2022-01-01",
                tilOgMed = "2022-12-31",
                client = this.client,
                appComponents = appComponents,
                iverksett = { _, _ -> SKIP_STEP },
            )

            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId = sakId,
                vedtakId = vedtakId,
                client = this.client,
                expectedSøknadId = søknadId,
                verifiserResponsVilkårAvslag = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
                postbody = omgjøringPgaNyeOpplysninger,
            )
        }
    }
}

private fun verifiserVedtak(vedtakJson: String, expectedBehandlingId: String) {
    val vedtak = deserialize<VedtakResponsJson>(vedtakJson)
    val beregning = vedtak.beregning.shouldNotBeNull()
    val forventetVedtak = VedtakResponsJson(
        id = vedtak.id,
        opprettet = vedtak.opprettet,
        beregning = BeregningJson(
            id = beregning.id,
            opprettet = beregning.opprettet,
            fraOgMed = stønadsperiode.fraOgMed.toString(),
            tilOgMed = stønadsperiode.tilOgMed.toString(),
            månedsberegninger = stønadsperiode.måneder().map { måned ->
                MånedsberegningJson(
                    fraOgMed = måned.fraOgMed.toString(),
                    tilOgMed = måned.tilOgMed.toString(),
                    sats = Satskategori.HØY.name,
                    grunnbeløp = 101351,
                    beløp = 0,
                    fradrag = privatPensjonOgForventetInntekt(måned),
                    satsbeløp = 20946,
                    epsFribeløp = 0.0,
                    epsInputFradrag = emptyList(),
                    merknader = listOf(MerknadJson.BeregningJson.BeløpErNullJson),
                    forventetInntektSammenligning = null,
                )
            },
            fradrag = privatPensjonOgForventetInntekt(stønadsperiode),
            begrunnelse = null,
        ),
        simulering = null,
        attestant = AUTOMATISK_ATTESTANT,
        saksbehandler = DEFAULT_IDENT,
        utbetalingId = null,
        behandlingId = UUID.fromString(expectedBehandlingId),
        periode = stønadsperiode.toJson(),
        type = VedtakTypeJson.AVSLAG.toString(),
        dokumenttilstand = Dokumenttilstand.GENERERT.name,
        kanStarteNyBehandling = true,
        skalSendeBrev = true,
    )
    val forventetBeregning = forventetVedtak.beregning.shouldNotBeNull()
    assertSoftly {
        withClue("vedtak") {
            vedtak.shouldBeEqualToIgnoringFields(forventetVedtak, VedtakResponsJson::beregning)
        }
        withClue("vedtak.beregning") {
            beregning.shouldBeEqualToIgnoringFields(forventetBeregning, BeregningJson::månedsberegninger)
        }
        withClue("vedtak.beregning.månedsberegninger: antall måneder") {
            beregning.månedsberegninger.size shouldBe forventetBeregning.månedsberegninger.size
        }
        beregning.månedsberegninger.zip(forventetBeregning.månedsberegninger)
            .forEachIndexed { indeks, (faktiskMåned, forventetMåned) ->
                withClue("vedtak.beregning.månedsberegninger[$indeks] (${forventetMåned.fraOgMed})") {
                    faktiskMåned.shouldBeEqualToComparingFields(forventetMåned)
                }
            }
    }
}

private fun privatPensjonOgForventetInntekt(periode: Periode): List<FradragResponseJson> = listOf(
    brukersFradrag(Fradragstype.Kategori.PrivatPensjon, beløp = PRIVAT_PENSJON_SOM_GIR_AVSLAG, periode = periode),
    brukersFradrag(Fradragstype.Kategori.ForventetInntekt, beløp = FORVENTET_INNTEKT, periode = periode),
)

private fun brukersFradrag(
    type: Fradragstype.Kategori,
    beløp: Double,
    periode: Periode,
) = FradragResponseJson(
    periode = periode.toJson(),
    type = type.name,
    beskrivelse = null,
    beløp = beløp,
    utenlandskInntekt = null,
    tilhører = FradragTilhører.BRUKER.name,
)
