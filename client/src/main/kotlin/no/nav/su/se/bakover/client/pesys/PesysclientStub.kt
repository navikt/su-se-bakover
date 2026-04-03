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
                    AlderBeregningsperiode(
                        netto = 20000,
                        fom = dato.minusMonths(6),
                        tom = dato,
                        grunnbelop = 130000,
                    ),
                    AlderBeregningsperiode(
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

    companion object {
        fun build(
            uførePeriode: List<UføreBeregningsperioderPerPerson> = emptyList(),
            alderPerioder: List<AlderBeregningsperioderPerPerson> = emptyList(),
        ): PesysClient {
            return object : PesysClient {
                override fun hentVedtakForPersonPaaDatoAlder(
                    fnrList: List<Fnr>,
                    dato: LocalDate,
                ): Either<ClientError, ResponseDtoAlder> {
                    return ResponseDtoAlder(alderPerioder).right()
                }

                override fun hentVedtakForPersonPaaDatoUføre(
                    fnrList: List<Fnr>,
                    dato: LocalDate,
                ): Either<ClientError, ResponseDtoUføre> {
                    return ResponseDtoUføre(uførePeriode).right()
                }
            }
        }
    }
}
