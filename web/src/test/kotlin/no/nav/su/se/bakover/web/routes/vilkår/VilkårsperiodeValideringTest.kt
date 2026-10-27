package no.nav.su.se.bakover.web.routes.vilkår

import arrow.core.Either
import arrow.core.left
import common.presentation.periode.toPeriodeOrResultat
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.FastOppholdINorgeVurderingJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.LeggTilVurderingsperiodeFastOppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.FlyktningVurderingJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.LeggTilVurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktBeskrivelseJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.VurderingsperiodeOpplysningspliktVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.PensjonsopplysningerJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.PensjonsoppysningerSvarJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.toDomain
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.UtenlandsoppholdBody
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.UtenlandsoppholdVurderingBody
import org.junit.jupiter.api.Test
import java.util.UUID

class VilkårsperiodeValideringTest {
    private val gyldigPeriode = PeriodeJson("2026-01-01", "2026-01-31")
    private val behandlingId = SøknadsbehandlingId(UUID.randomUUID())
    private val pensjonsopplysninger = PensjonsopplysningerJson(
        folketrygd = PensjonsoppysningerSvarJson.JA,
        andreNorske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
        utenlandske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
    )

    @Test
    fun `ugyldige datoer og perioder returnerer første konkrete valideringsfeil i vilkårsmappere`() {
        listOf(
            gyldigPeriode.copy(fraOgMed = "ugyldig") to "ugyldig_datoformat",
            gyldigPeriode.copy(tilOgMed = "2026-02-30") to "ugyldig_datoformat",
            gyldigPeriode.copy(fraOgMed = "2026-01-02") to "ugyldig_periode_fom",
            gyldigPeriode.copy(tilOgMed = "2026-01-30") to "ugyldig_periode_tom",
            gyldigPeriode.copy(fraOgMed = "2026-02-01") to "ugyldig_periode_start_slutt",
        ).forEach { (periode, forventetKode) ->
            val resultater = listOf(periode.toPeriodeOrResultat()) + konverterVilkår(periode)
            resultater.forEach { resultat ->
                resultat.mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
                resultat.mapLeft { deserialize<ErrorJson>(it.json).code } shouldBe forventetKode.left()
            }
        }
    }

    @Test
    fun `gyldige vurderingsperioder aksepteres fortsatt i alle mappere`() {
        konverterVilkår(gyldigPeriode).forEach { it.isRight() shouldBe true }
    }

    private fun konverterVilkår(periode: PeriodeJson): List<Either<Resultat, *>> = listOf(
        listOf(LeggTilVurderingsperiodeFastOppholdJson(periode, FastOppholdINorgeVurderingJson.VilkårOppfylt))
            .toDomain(fixedClock),
        listOf(LeggTilVurderingsperiodeFlyktningVilkårJson(periode, FlyktningVurderingJson.VilkårOppfylt))
            .toDomain(fixedClock),
        listOf(LeggTilVurderingsperiodePersonligOppmøteJson(periode, PersonligOppmøteÅrsakJson.MøttPersonlig))
            .toDomain(fixedClock),
        listOf(LeggTilVurderingsperiodePensjonsvilkårJson(periode, pensjonsopplysninger)).toDomain(fixedClock),
        listOf(
            VurderingsperiodeOpplysningspliktVilkårJson(periode, OpplysningspliktBeskrivelseJson.TilstrekkeligDokumentasjon),
        ).toDomain(fixedClock),
        LeggTilVurderingsperiodeInstitusjonsoppholdJson(
            listOf(VurderingsperiodeInstitusjonsoppholdJson(periode, VurderingInstitusjonsoppholdJson.VilkårOppfylt)),
        ).toDomain(fixedClock),
        UtenlandsoppholdBody(
            listOf(UtenlandsoppholdVurderingBody(periode, UtenlandsoppholdStatus.SkalHoldeSegINorge)),
        ).toDomain(behandlingId),
    )
}
