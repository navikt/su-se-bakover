package no.nav.su.se.bakover.service.regulering

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
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
        reguleringsMåned: Måned,
        saker: List<Sak>,
    ): Reguleringssupplement {
        // TODO feilhåndtering..
        val månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1)

        val fnrList = saker.map { sak ->
            sak.hentGjeldendeVedtaksdata(periode = reguleringsMåned, clock = clock).map { gjeldendeVedtaksdata ->
                listOf(sak.fnr) + gjeldendeVedtaksdata.grunnlagsdata.eps
            } // TODO distinct
        }.filterRights().flatten()

        // TODO Må resultat fra uføre og alder mappes til et fnr? Trolig ja, spesielt når vi henter aap ??

        val uføre = pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrList,
            dato = månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat

        val uføreSuppl = uføre.map {
            ReguleringssupplementFor(
                fnr = Fnr(it.fnr),
                perType = nonEmptyListOf(
                    ReguleringssupplementFor.PerType(
                        kategori = Fradragstype.Kategori.Uføretrygd,
                        vedtak = it.perioder.map { vedtaksperiode ->
                            Eksternvedtak.Endring(
                                periode = PeriodeMedOptionalTilOgMed(vedtaksperiode.fom, vedtaksperiode.tom),
                                fradrag = nonEmptyListOf(
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = vedtaksperiode.fom,
                                        tilOgMed = vedtaksperiode.tom,
                                        beløp = vedtaksperiode.netto,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = TODO(),
                                    ),
                                ),
                                beløp = vedtaksperiode.netto,
                            )
                        }.toNonEmptyList(),
                    ),
                ),
            )
        }

        val alder = pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrList,
            dato = månedFørRegulering,
        ).getOrElse {
            // TODO
            throw Exception("")
        }.resultat
        val alderSuppl = alder.map {
            ReguleringssupplementFor(
                fnr = Fnr(it.fnr),
                perType = it.perioder.map { vedtaksperiode ->
                    ReguleringssupplementFor.PerType(
                        kategori = Fradragstype.Kategori.Alderspensjon,
                        vedtak = nonEmptyListOf(
                            Eksternvedtak.Endring(
                                periode = PeriodeMedOptionalTilOgMed(vedtaksperiode.fom, vedtaksperiode.tom),
                                fradrag = nonEmptyListOf(
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = vedtaksperiode.fom,
                                        tilOgMed = vedtaksperiode.tom,
                                        beløp = vedtaksperiode.netto,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = TODO(),
                                    ),
                                ),
                                beløp = vedtaksperiode.netto,
                            ),
                        ),
                    )
                }.toNonEmptyList(),
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
