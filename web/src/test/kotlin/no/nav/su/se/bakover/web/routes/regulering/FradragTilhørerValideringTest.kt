package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.left
import arrow.core.right
import common.presentation.beregning.FradragRequestJson
import common.presentation.beregning.toFradragTilhører
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.FradragBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.FradragsgrunnlagJson
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID

class FradragTilhørerValideringTest {
    @Test
    fun `gyldige eiere bevares`() {
        FradragTilhører.entries.forEach { tilhører ->
            tilhører.name.toFradragTilhører() shouldBe tilhører.right()
        }
    }

    @Test
    fun `ukjent eier gir bad request i alle inputmappere`() {
        val periode = januar(2026)
        val periodeJson = PeriodeJson("2026-01-01", "2026-01-31")
        val fradragstype = Fradragstype.Kategori.Arbeidsinntekt.name
        val ugyldigTilhører = "ukjent"
        val beløp = 100.0
        val forventet = HttpStatusCode.BadRequest.errorJson(
            "Ugyldig verdi for hvem fradraget tilhører",
            "ugyldig_fradrag_tilhører",
        ).left()
        ugyldigTilhører.toFradragTilhører() shouldBe forventet
        LegacyFradragRequestJson(
            periodeJson,
            fradragstype,
            null,
            beløp,
            null,
            ugyldigTilhører,
        ).toFradrag(periode) shouldBe forventet
        FradragRequestJson(
            periodeJson,
            fradragstype,
            null,
            beløp,
            null,
            ugyldigTilhører,
        ).toFradrag(periode) shouldBe forventet
        FradragBody(
            listOf(FradragsgrunnlagJson(periodeJson, fradragstype, null, beløp, null, ugyldigTilhører)),
        ).toCommand(UUID.randomUUID(), fixedClock) shouldBe forventet
    }
}
