package no.nav.su.se.bakover.client.stubs.regoppslag

import arrow.core.right
import no.nav.su.se.bakover.client.regoppslag.RegoppslagKlient
import no.nav.su.se.bakover.client.regoppslag.RegoppslagResponseDTO
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr

object RegoppslagKlientStub : RegoppslagKlient {
    override suspend fun hentMottakerAdresse(
        sakType: Sakstype,
        ident: Fnr,
    ) = RegoppslagResponseDTO(
        navn = "Stub Person",
        adresse = RegoppslagResponseDTO.Adresse(
            type = RegoppslagResponseDTO.AdresseType.NORSKPOSTADRESSE,
            adresseKilde = RegoppslagResponseDTO.AdresseKilde.BOSTEDSADRESSE,
            adresselinje1 = "Storgata 1",
            adresselinje2 = null,
            adresselinje3 = null,
            postnummer = "0150",
            poststed = "Oslo",
            landkode = "NO",
            land = "Norge",
        ),
    ).right()
}
