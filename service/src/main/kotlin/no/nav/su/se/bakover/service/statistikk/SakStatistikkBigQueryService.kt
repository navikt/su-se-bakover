package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import org.slf4j.LoggerFactory
import java.time.LocalDate

private const val MAKS_ANTALL_SEKVENS_IDER = 500

interface SakStatistikkBigQueryService {
    fun lastTilBigQuery(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate = fraOgMed,
    )

    fun erstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ErstattetSakStatistikk>

    fun forhåndsvisErstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ForhåndsvisErstattSakStatistikk>
}

sealed interface KunneIkkeErstatteSakStatistikk {
    data object IngenSekvensIder : KunneIkkeErstatteSakStatistikk
    data class ForMangeSekvensIder(val maksimaltAntall: Int) : KunneIkkeErstatteSakStatistikk
    data class UgyldigeSekvensIder(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class DuplikateSekvensIder(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class AvvikISakStatistikk(
        val manglendeSekvensIder: Set<Long>,
        val ikkeUnikeSekvensIder: Set<Long>,
        val uventedeSekvensIder: Set<Long>,
    ) : KunneIkkeErstatteSakStatistikk

    data class UgyldigTilstandIBigQuery(
        val antallForespurte: Int,
        val antallRaderIBigQuery: Long,
        val manglendeSekvensIder: Set<Long>,
        val ikkeUnikeSekvensIder: Set<Long>,
        val uventedeSekvensIder: Set<Long>,
    ) : KunneIkkeErstatteSakStatistikk
}

data class ErstattetSakStatistikk(val antall: Int)

data class ForhåndsvisErstattSakStatistikk(
    val kanErstattes: Boolean,
    val antallForespurte: Int,
    val antallRaderISakStatistikk: Int,
    val antallRaderIBigQuery: Long?,
    val manglendeISakStatistikk: List<Long>,
    val ikkeUnikeISakStatistikk: List<Long>,
    val manglendeIBigQuery: List<Long>?,
    val ikkeUnikeIBigQuery: List<Long>?,
)

private data class PostgresErstatningskontroll(
    val data: List<SakStatistikk>,
    val manglendeSekvensIder: List<Long>,
    val ikkeUnikeSekvensIder: List<Long>,
)

private data class BigQueryErstatningskontroll(
    val sekvensIder: List<Long>,
    val raderPerSekvensId: Map<Long, Long>,
) {
    val antallRader = raderPerSekvensId.values.sum()
    val manglendeSekvensIder = sekvensIder.filterNot(raderPerSekvensId::containsKey)
    val ikkeUnikeSekvensIder = raderPerSekvensId.filterValues { it > 1L }.keys.sorted()
}

/*
Skal inn i cron jobs og kjøre en gang hver natt 01:00
 */
// https://docs.nais.io/workloads/application/reference/application-spec/#gcpbigquerydatasets
class SakStatistikkBigQueryServiceImpl(
    private val repo: SakStatistikkRepo,
    private val bigQueryGateway: SakStatistikkBigQueryGateway,
) : SakStatistikkBigQueryService {
    private val logger = LoggerFactory.getLogger(SakStatistikkBigQueryService::class.java)

    override fun lastTilBigQuery(fraOgMed: LocalDate, tilOgMed: LocalDate) {
        val data = repo.hentSakStatistikk(fraOgMed, tilOgMed)
        logger.info("Hentet ${data.size} rader fra databasen")
        if (data.isNotEmpty()) {
            bigQueryGateway.writeToBigQuery(data)
        }
        logger.info("Slutter jobb Saksstatistikk")
    }

    override fun erstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ErstattetSakStatistikk> {
        val validerteSekvensIder = validerSekvensIder(sekvensIder).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val postgresKontroll = kontrollerPostgres(sekvensIder, validerteSekvensIder).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val data = validerPostgresForErstatning(postgresKontroll).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val bigQueryKontroll =
            kontrollerBigQuery(sekvensIder, validerteSekvensIder, bigQueryGateway).fold(
                ifLeft = { return it.left() },
                ifRight = { it },
            )
        validerBigQueryForErstatning(bigQueryKontroll).fold(
            ifLeft = { return it.left() },
            ifRight = { },
        )

        bigQueryGateway.deleteExactlyOrVerifyMissing(validerteSekvensIder)
        bigQueryGateway.writeToBigQuery(data)
        try {
            bigQueryGateway.verifyExactlyOnce(validerteSekvensIder)
        } catch (e: Exception) {
            logger.error(
                "Opplastingen til BigQuery er utført, men etterkontrollen feilet for ${validerteSekvensIder.size} sekvens-ID-er",
                e,
            )
            throw e
        }

        logger.info("Erstattet ${data.size} rader i BigQuery")
        return ErstattetSakStatistikk(data.size).right()
    }

    override fun forhåndsvisErstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ForhåndsvisErstattSakStatistikk> {
        val validerteSekvensIder = validerSekvensIder(sekvensIder).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val postgresKontroll = kontrollerPostgres(sekvensIder, validerteSekvensIder).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        if (validerPostgresForErstatning(postgresKontroll).isLeft()) {
            return ForhåndsvisErstattSakStatistikk(
                kanErstattes = false,
                antallForespurte = sekvensIder.size,
                antallRaderISakStatistikk = postgresKontroll.data.size,
                antallRaderIBigQuery = null,
                manglendeISakStatistikk = postgresKontroll.manglendeSekvensIder,
                ikkeUnikeISakStatistikk = postgresKontroll.ikkeUnikeSekvensIder,
                manglendeIBigQuery = null,
                ikkeUnikeIBigQuery = null,
            ).right()
        }
        val bigQueryKontroll = kontrollerBigQuery(
            sekvensIder = sekvensIder,
            validerteSekvensIder = validerteSekvensIder,
            bigQueryGateway = bigQueryGateway,
        ).fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val bigQueryKanErstattes = validerBigQueryForErstatning(bigQueryKontroll).isRight()

        return ForhåndsvisErstattSakStatistikk(
            kanErstattes = bigQueryKanErstattes,
            antallForespurte = sekvensIder.size,
            antallRaderISakStatistikk = postgresKontroll.data.size,
            antallRaderIBigQuery = bigQueryKontroll.antallRader,
            manglendeISakStatistikk = postgresKontroll.manglendeSekvensIder,
            ikkeUnikeISakStatistikk = postgresKontroll.ikkeUnikeSekvensIder,
            manglendeIBigQuery = bigQueryKontroll.manglendeSekvensIder,
            ikkeUnikeIBigQuery = bigQueryKontroll.ikkeUnikeSekvensIder,
        ).right()
    }

    private fun kontrollerPostgres(
        sekvensIder: List<Long>,
        validerteSekvensIder: Set<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk, PostgresErstatningskontroll> {
        val data = repo.hentSakStatistikk(validerteSekvensIder)
        val raderPerSekvensId = data.groupingBy { it.getSekvensId().longValueExact() }.eachCount()
        val uventedeSekvensIder = raderPerSekvensId.keys - validerteSekvensIder
        val dupliserteSekvensIder = raderPerSekvensId.filterValues { it > 1 }.keys
        val kontroll = PostgresErstatningskontroll(
            data = data,
            manglendeSekvensIder = sekvensIder.filterNot(raderPerSekvensId::containsKey),
            ikkeUnikeSekvensIder = sekvensIder.filter(dupliserteSekvensIder::contains),
        )
        if (uventedeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk(
                manglendeSekvensIder = kontroll.manglendeSekvensIder.toSet(),
                ikkeUnikeSekvensIder = kontroll.ikkeUnikeSekvensIder.toSet(),
                uventedeSekvensIder = uventedeSekvensIder,
            ).left()
        }
        return kontroll.right()
    }

    private fun kontrollerBigQuery(
        sekvensIder: List<Long>,
        validerteSekvensIder: Set<Long>,
        bigQueryGateway: SakStatistikkBigQueryGateway,
    ): Either<KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery, BigQueryErstatningskontroll> {
        val raderPerSekvensId = bigQueryGateway.hentAntallRaderPerSekvensId(validerteSekvensIder)
        val uventedeSekvensIder = raderPerSekvensId.keys - validerteSekvensIder
        val kontroll = BigQueryErstatningskontroll(
            sekvensIder = sekvensIder,
            raderPerSekvensId = raderPerSekvensId,
        )
        if (uventedeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery(
                antallForespurte = sekvensIder.size,
                antallRaderIBigQuery = kontroll.antallRader,
                manglendeSekvensIder = kontroll.manglendeSekvensIder.toSet(),
                ikkeUnikeSekvensIder = kontroll.ikkeUnikeSekvensIder.toSet(),
                uventedeSekvensIder = uventedeSekvensIder,
            ).left()
        }
        return kontroll.right()
    }

    private fun validerPostgresForErstatning(
        kontroll: PostgresErstatningskontroll,
    ): Either<KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk, List<SakStatistikk>> =
        if (kontroll.manglendeSekvensIder.isEmpty() && kontroll.ikkeUnikeSekvensIder.isEmpty()) {
            kontroll.data.right()
        } else {
            KunneIkkeErstatteSakStatistikk.AvvikISakStatistikk(
                manglendeSekvensIder = kontroll.manglendeSekvensIder.toSet(),
                ikkeUnikeSekvensIder = kontroll.ikkeUnikeSekvensIder.toSet(),
                uventedeSekvensIder = emptySet(),
            ).left()
        }

    private fun validerBigQueryForErstatning(
        kontroll: BigQueryErstatningskontroll,
    ): Either<KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery, Unit> {
        val alleMangler = kontroll.manglendeSekvensIder.size == kontroll.sekvensIder.size
        val alleFinnesNøyaktigEnGang =
            kontroll.manglendeSekvensIder.isEmpty() && kontroll.ikkeUnikeSekvensIder.isEmpty()
        return if (alleMangler || alleFinnesNøyaktigEnGang) {
            Unit.right()
        } else {
            KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery(
                antallForespurte = kontroll.sekvensIder.size,
                antallRaderIBigQuery = kontroll.antallRader,
                manglendeSekvensIder = kontroll.manglendeSekvensIder.toSet(),
                ikkeUnikeSekvensIder = kontroll.ikkeUnikeSekvensIder.toSet(),
                uventedeSekvensIder = emptySet(),
            ).left()
        }
    }

    private fun validerSekvensIder(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, Set<Long>> {
        if (sekvensIder.isEmpty()) {
            return KunneIkkeErstatteSakStatistikk.IngenSekvensIder.left()
        }
        if (sekvensIder.size > MAKS_ANTALL_SEKVENS_IDER) {
            return KunneIkkeErstatteSakStatistikk.ForMangeSekvensIder(MAKS_ANTALL_SEKVENS_IDER).left()
        }
        val ugyldigeSekvensIder = sekvensIder.filter { it <= 0 }.toSet()
        if (ugyldigeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.UgyldigeSekvensIder(ugyldigeSekvensIder).left()
        }
        val duplikateSekvensIder = sekvensIder.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplikateSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.DuplikateSekvensIder(duplikateSekvensIder).left()
        }
        return sekvensIder.toSet().right()
    }
}
