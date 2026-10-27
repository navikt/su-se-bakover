package no.nav.su.se.bakover.web.routes.vilkår.pensjon

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import vilkår.pensjon.domain.Pensjonsopplysninger

class PensjonsopplysningerValideringTest {
    private val periode = PeriodeJson("2026-01-01", "2026-01-31")

    @Test
    fun `folketrygd godtar ja og nei`() {
        PensjonsoppysningerSvarJson.JA.toFolketrygdSvar() shouldBe
            Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden.right()
        PensjonsoppysningerSvarJson.NEI.toFolketrygdSvar() shouldBe
            Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden.right()
    }

    @Test
    fun `ikke aktuelt for folketrygd bevares som typed feil og bad request`() {
        val opplysninger = PensjonsopplysningerJson(
            folketrygd = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
            andreNorske = PensjonsoppysningerSvarJson.JA,
            utenlandske = PensjonsoppysningerSvarJson.NEI,
        )
        val forventetFeil = KunneIkkeLeggeTilPensjonsVilkår.UgyldigFolketrygdsvar
        opplysninger.toDomain() shouldBe forventetFeil.left()

        val resultat = listOf(LeggTilVurderingsperiodePensjonsvilkårJson(periode, opplysninger)).toDomain(fixedClock)
        resultat.mapLeft { it.httpCode } shouldBe HttpStatusCode.BadRequest.left()
        resultat.mapLeft { deserialize<ErrorJson>(it.json).code } shouldBe "ugyldig_folketrygdsvar".left()
    }

    @Test
    fun `andre pensjoner kan fortsatt være ikke aktuelt`() {
        val opplysninger = PensjonsopplysningerJson(
            folketrygd = PensjonsoppysningerSvarJson.JA,
            andreNorske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
            utenlandske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
        )

        listOf(LeggTilVurderingsperiodePensjonsvilkårJson(periode, opplysninger)).toDomain(fixedClock)
            .isRight() shouldBe true
    }
}
