package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.pensjon.domain.Pensjonsopplysninger

internal class PensjonsgrunnlagPostgresRepoTest {

    @Test
    fun `jsonformat for grunnlag`() {
        JSONAssert.assertEquals(
            serialize(
                Pensjonsopplysninger(
                    søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(svar = Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarIkkeSøktPensjonFraFolketrygden),
                    søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(svar = Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt),
                    søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(svar = Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner),
                ).toDb(),
            ),
            """
            {
                "folketrygd": "HAR_IKKE_SØKT_PENSJON_FRA_FOLKETRYGDEN",
                "andreNorske": "IKKE_AKTUELT",
                "utenlandske": "HAR_SØKT_UTENLANDSKE_PENSJONER"
            }
        """,
            true,
        )
    }
}
