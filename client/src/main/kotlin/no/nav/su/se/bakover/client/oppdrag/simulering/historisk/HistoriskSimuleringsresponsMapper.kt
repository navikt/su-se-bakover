package no.nav.su.se.bakover.client.oppdrag.simulering.historisk

import no.nav.su.se.bakover.client.oppdrag.simulering.SimulerBeregningResponse
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsrespons
import java.time.LocalDate

internal fun SimulerBeregningResponse.tilHistoriskRespons(råRespons: String) = HistoriskSimuleringsrespons(
    beregning = response?.simulering?.let { beregning ->
        HistoriskSimuleringsrespons.Beregning(
            gjelderId = Fnr(beregning.gjelderId),
            gjelderNavn = beregning.gjelderNavn,
            datoBeregnet = LocalDate.parse(beregning.datoBeregnet),
            faggruppe = beregning.kodeFaggruppe,
            beløp = beregning.belop.toBigDecimal(),
            perioder = beregning.beregningsPeriode.map { periode ->
                HistoriskSimuleringsrespons.Beregningsperiode(
                    fraOgMed = LocalDate.parse(periode.periodeFom),
                    tilOgMed = LocalDate.parse(periode.periodeTom),
                    utbetalinger = periode.beregningStoppnivaa.map { utbetaling ->
                        HistoriskSimuleringsrespons.Utbetaling(
                            fagområde = utbetaling.kodeFagomraade,
                            stoppnivåId = utbetaling.stoppNivaaId,
                            behandlendeEnhet = utbetaling.behandlendeEnhet,
                            oppdragsId = utbetaling.oppdragsId,
                            fagsystemId = utbetaling.fagsystemId,
                            kid = utbetaling.kid,
                            utbetalesTilId = utbetaling.utbetalesTilId,
                            utbetalesTilNavn = utbetaling.utbetalesTilNavn,
                            bilagstype = utbetaling.bilagsType,
                            forfall = LocalDate.parse(utbetaling.forfall),
                            feilkonto = utbetaling.feilkonto.toBooleanStrict(),
                            detaljer = utbetaling.beregningStoppnivaaDetaljer.map { detalj ->
                                HistoriskSimuleringsrespons.Detalj(
                                    faktiskFraOgMed = LocalDate.parse(detalj.faktiskFom),
                                    faktiskTilOgMed = LocalDate.parse(detalj.faktiskTom),
                                    konto = detalj.kontoStreng,
                                    behandlingskode = detalj.behandlingskode,
                                    beløp = detalj.belop.toBigDecimal(),
                                    trekkVedtakId = detalj.trekkVedtakId,
                                    stønadId = detalj.stonadId,
                                    korrigering = detalj.korrigering,
                                    tilbakeføring = detalj.tilbakeforing.toBooleanStrict(),
                                    linjeId = detalj.linjeId,
                                    sats = detalj.sats.toBigDecimal(),
                                    typeSats = detalj.typeSats,
                                    antallSats = detalj.antallSats.toBigDecimal(),
                                    saksbehandlerId = detalj.saksbehId,
                                    uføregrad = detalj.uforeGrad.toBigDecimal(),
                                    kravhaverId = detalj.kravhaverId,
                                    delytelseId = detalj.delytelseId,
                                    bostedsenhet = detalj.bostedsenhet,
                                    skyldnerId = detalj.skykldnerId,
                                    klassekode = detalj.klassekode,
                                    klassekodeBeskrivelse = detalj.klasseKodeBeskrivelse,
                                    klasseType = detalj.typeKlasse,
                                    klasseTypeBeskrivelse = detalj.typeKlasseBeskrivelse,
                                    refunderesOrgNr = detalj.refunderesOrgNr,
                                )
                            },
                        )
                    },
                )
            },
        )
    },
    infomelding = response?.infomelding?.beskrMelding,
    råRespons = råRespons,
)
