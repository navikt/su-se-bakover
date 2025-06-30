package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.domain.statistikk.StønadRepo
import statistikk.domain.StønadstatistikkDto
import java.util.UUID

class StønadRepoPostgres(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StønadRepo {
    override fun lagreHendelse(dto: StønadstatistikkDto) {
        return dbMetrics.timeQuery("lagreHendelseStønadstatistikkDto") {
            sessionFactory.withSession { session ->
                val stoendStatistikkId = UUID.randomUUID()
                """
                    INSERT INTO stoend_statistikk (
                    id, har_utenlands_opp_hold, har_familiegjenforening, statistikk_aar_maaned, personnummer,
                    personnummer_ektefelle, funksjonell_tid, teknisk_tid, stonadstype, sak_id, vedtaksdato,
                    vedtakstype, vedtaksresultat, behandlende_enhet_kode, ytelse_virkningstidspunkt,
                    gjeldende_stonad_virkningstidspunkt, gjeldende_stonad_stopptidspunkt,
                    gjeldende_stonad_utbetalingsstart, gjeldende_stonad_utbetalingsstopp, opphorsgrunn,
                    opphorsdato, flyktningsstatus, versjon
                    ) VALUES (
                        :id, :har_utenlands_opp_hold, :har_familiegjenforening, :statistikk_aar_maaned, :personnummer,
                        :personnummer_ektefelle, :funksjonell_tid, :teknisk_tid, :stonadstype, :sak_id, :vedtaksdato,
                        :vedtakstype, :vedtaksresultat, :behandlende_enhet_kode, :ytelse_virkningstidspunkt,
                        :gjeldende_stonad_virkningstidspunkt, :gjeldende_stonad_stopptidspunkt,
                        :gjeldende_stonad_utbetalingsstart, :gjeldende_stonad_utbetalingsstopp, :opphorsgrunn,
                        :opphorsdato, :flyktningsstatus, :versjon
                    )
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to stoendStatistikkId,
                            "har_utenlands_opp_hold" to dto.harUtenlandsOpphold?.name,
                            "har_familiegjenforening" to dto.harFamiliegjenforening?.name,
                            "statistikk_aar_maaned" to dto.statistikkAarMaaned.atDay(1),
                            "personnummer" to dto.personnummer.toString(),
                            "personnummer_ektefelle" to dto.personNummerEktefelle?.toString(),
                            "funksjonell_tid" to dto.funksjonellTid.toString(),
                            "teknisk_tid" to dto.tekniskTid.toString(),
                            "stonadstype" to dto.stonadstype.name,
                            "sak_id" to dto.sakId,
                            "vedtaksdato" to dto.vedtaksdato,
                            "vedtakstype" to dto.vedtakstype.name,
                            "vedtaksresultat" to dto.vedtaksresultat.name,
                            "behandlende_enhet_kode" to dto.behandlendeEnhetKode,
                            "ytelse_virkningstidspunkt" to dto.ytelseVirkningstidspunkt,
                            "gjeldende_stonad_virkningstidspunkt" to dto.gjeldendeStonadVirkningstidspunkt,
                            "gjeldende_stonad_stopptidspunkt" to dto.gjeldendeStonadStopptidspunkt,
                            "gjeldende_stonad_utbetalingsstart" to dto.gjeldendeStonadUtbetalingsstart,
                            "gjeldende_stonad_utbetalingsstopp" to dto.gjeldendeStonadUtbetalingsstopp,
                            "opphorsgrunn" to dto.opphorsgrunn,
                            "opphorsdato" to dto.opphorsdato,
                            "flyktningsstatus" to dto.flyktningsstatus,
                            "versjon" to dto.versjon,
                        ),
                        session = session,
                    )

                dto.månedsbeløp.forEach { mb ->
                    val manedsbelopId = UUID.randomUUID()
                    """
                    INSERT INTO manedsbelop (
                        id, stoend_statistikk_id, maaned, stonadsklassifisering, bruttosats, nettosats, fradrag_sum
                    ) VALUES (
                        :id, :stoend_statistikk_id, :maaned, :stonadsklassifisering, :bruttosats, :nettosats, :fradrag_sum
                    )
                    """.trimIndent()
                        .insert(
                            mapOf(
                                "id" to manedsbelopId,
                                "stoend_statistikk_id" to stoendStatistikkId,
                                "maaned" to mb.måned,
                                "stonadsklassifisering" to mb.stonadsklassifisering.name,
                                "bruttosats" to mb.bruttosats,
                                "nettosats" to mb.nettosats,
                                "fradrag_sum" to mb.fradragSum,
                            ),
                            session = session,
                        )
                    mb.inntekter.forEach { inntekt ->
                        val inntektId = UUID.randomUUID()
                        """
                        INSERT INTO inntekt (
                            id, manedsbelop_id, inntektstype, belop, tilhorer, er_utenlandsk
                        ) VALUES (
                            :id, :manedsbelop_id, :inntektstype, :belop, :tilhorer, :er_utenlandsk
                        )
                        """.trimIndent()
                            .insert(
                                mapOf(
                                    "id" to inntektId,
                                    "manedsbelop_id" to manedsbelopId,
                                    "inntektstype" to inntekt.inntektstype,
                                    "belop" to inntekt.beløp,
                                    "tilhorer" to inntekt.tilhører,
                                    "er_utenlandsk" to inntekt.erUtenlandsk,
                                ),
                                session = session,
                            )
                    }
                }
            }
        }
    }
}
