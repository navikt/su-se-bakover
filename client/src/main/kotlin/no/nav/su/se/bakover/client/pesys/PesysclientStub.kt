package no.nav.su.se.bakover.client.pesys

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate

class PesysclientStub : PesysClient {
    override fun hentVedtakForPersonPaaDatoAlder(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDtoAlder> {
        val resultat = fnrList.map { fnr ->
            AlderBeregningsperioderPerPerson(
                fnr = fnr.toString(),
                perioder = listOf(
                    BeregningsperiodeDto(
                        netto = 20000,
                        fom = dato.minusMonths(6),
                        tom = dato,
                        grunnbelop = 130000,
                    ),
                    BeregningsperiodeDto(
                        netto = 20000,
                        fom = dato.plusMonths(1),
                        tom = null,
                        grunnbelop = 130000,
                    ),
                ),
            )
        }
        return ResponseDtoAlder(resultat).right()
    }

    override fun hentVedtakForPersonPaaDatoUføre(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDtoUføre> {
        val resultat = fnrList.map { fnr ->
            UføreBeregningsperioderPerPerson(
                fnr = fnr.toString(),
                perioder = listOf(
                    UføreBeregningsperiode(
                        netto = 20000,
                        fom = dato.minusMonths(6),
                        tom = dato,
                        grunnbelop = 130000,
                        oppjustertInntektEtterUfore = 10000,
                    ),
                    UføreBeregningsperiode(
                        netto = 20000,
                        fom = dato.plusMonths(1),
                        tom = null,
                        grunnbelop = 130000,
                        oppjustertInntektEtterUfore = 10000,
                    ),
                ),
            )
        }
        return ResponseDtoUføre(resultat).right()
    }
}
