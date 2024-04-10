package dokument.domain.pdf

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.person.Fnr
import java.time.Clock
import java.time.LocalDate

data class PersonaliaPdfInnhold(
    val dato: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val saksnummer: Long,
) {
    companion object {
        fun lagPersonalia(
            fødselsnummer: Fnr,
            saksnummer: Saksnummer,
            fornavn: String,
            etternavn: String,
            clock: Clock,
        ) = PersonaliaPdfInnhold(
            dato = LocalDate.now(clock).ddMMyyyy(),
            fødselsnummer = fødselsnummer.toString(),
            fornavn = fornavn,
            etternavn = etternavn,
            saksnummer = saksnummer.nummer,
        )
    }
}
