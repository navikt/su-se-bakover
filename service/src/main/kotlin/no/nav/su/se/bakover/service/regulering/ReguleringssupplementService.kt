package no.nav.su.se.bakover.service.regulering

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.util.UUID

class ReguleringssupplementService(
    private val pesysClient: PesysClient,
    private val clock: Clock,
) {

    fun hentAutomatiske(
        fraOgMedMåned: Måned,
        saker: List<Sak>,
    ): Reguleringssupplement {
        // TODO feilhåndtering..

        val fnrList = saker.map { sak ->
            sak.hentGjeldendeVedtaksdata(periode = fraOgMedMåned, clock = clock).mapLeft { feil ->
                feil
            }.map { gjeldendeVedtaksdata ->
                listOf(sak.fnr) + gjeldendeVedtaksdata.grunnlagsdata.eps
            }
        }.filterRights().flatten()

        val uføre = pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrList,
            dato = fraOgMedMåned.fraOgMed.minusMonths(1),
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat
        val uføreSuppl = uføre.map {
            ReguleringssupplementFor(
                fnr = Fnr(it.fnr),
                perType = it.perioder.map { vedteksPeriode ->
                    ReguleringssupplementFor.PerType(
                        kategori = Fradragstype.Kategori.Uføretrygd,
                        vedtak = nonEmptyListOf(
                            Eksternvedtak.Endring(
                                periode = PeriodeMedOptionalTilOgMed(vedteksPeriode.fom, vedteksPeriode.tom),
                                fradrag = nonEmptyListOf(
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = vedteksPeriode.fom,
                                        tilOgMed = vedteksPeriode.tom,
                                        beløp = vedteksPeriode.netto,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = TODO(),
                                    ),
                                ),
                                beløp = vedteksPeriode.netto,
                            ),
                        ),
                    )
                },
            )
        }

        val alder = pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrList,
            dato = fraOgMedMåned.fraOgMed.minusMonths(1),
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat
        val alderSuppl = alder.map {
            ReguleringssupplementFor(
                fnr = Fnr(it.fnr),
                perType = it.perioder.map { vedteksPeriode ->
                    ReguleringssupplementFor.PerType(
                        kategori = Fradragstype.Kategori.Uføretrygd,
                        vedtak = nonEmptyListOf(
                            Eksternvedtak.Endring(
                                periode = PeriodeMedOptionalTilOgMed(vedteksPeriode.fom, vedteksPeriode.tom),
                                fradrag = nonEmptyListOf(
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = vedteksPeriode.fom,
                                        tilOgMed = vedteksPeriode.tom,
                                        beløp = vedteksPeriode.netto,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = TODO(),
                                    ),
                                ),
                                beløp = vedteksPeriode.netto,
                            ),
                        ),
                    )
                },
            )
        }

        return Reguleringssupplement(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            supplement = uføreSuppl + alderSuppl,
            originalCsv = "",
        )
    }
}
