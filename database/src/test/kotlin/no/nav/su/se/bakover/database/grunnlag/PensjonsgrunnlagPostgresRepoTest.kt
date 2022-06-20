package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsopplysninger
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class PensjonsgrunnlagPostgresRepoTest {

    @Test
    fun `jsonformat for grunnlag`() {
        JSONAssert.assertEquals(
            objectMapper.writeValueAsString(
                Pensjonsopplysninger(
                    folketrygd = Pensjonsopplysninger.Folketrygd(svar = Pensjonsopplysninger.Svar.Nei),
                    andreNorske = Pensjonsopplysninger.AndreNorske(svar = Pensjonsopplysninger.Svar.IkkeAktuelt),
                    utenlandske = Pensjonsopplysninger.Utenlandske(svar = Pensjonsopplysninger.Svar.Ja),
                ).toDb(),
            ),
            """
            {
                "folketrygd": "NEI",
                "andreNorske": "IKKE_AKTUELT",
                "utenlandske": "JA"
            }
        """,
            true,
        )
    }
}
