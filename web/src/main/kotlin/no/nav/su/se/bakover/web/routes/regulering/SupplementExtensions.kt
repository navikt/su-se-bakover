package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import java.time.Clock
import java.util.UUID

fun parseCSVFromString(csv: String, clock: Clock): Either<Resultat, Reguleringssupplement> = parseCSV(csv.split(Regex("\r?\n")), clock)

private fun parseCSV(csv: List<String>, clock: Clock): Either<Resultat, Reguleringssupplement> {
    require(csv.first() == "FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK")
    val supplementId = UUID.randomUUID()
    return csv.drop(1).map {
        val splitted = it.split(";")
        PesysUtrekkFromCsv(
            fnr = splitted[0],
            sakstype = splitted[1],
            vedtakstype = splitted[2],
            fraOgMed = splitted[3],
            tilOgMed = splitted[4].isEmpty().whenever(
                isTrue = { null },
                isFalse = { splitted[4] },
            ),
            bruttoYtelse = splitted[5],
            nettoYtelse = splitted[6],
            ytelseskomponenttype = splitted[7],
            bruttoYtelseskomponent = splitted[8],
            nettoYtelseskomponent = splitted[9],
        )
    }.toDomain().map { Reguleringssupplement(supplementId, Tidspunkt.now(clock), it) }
}
