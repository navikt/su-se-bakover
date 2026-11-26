package no.nav.su.se.bakover.test.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

fun <T> withTestConnectionPool(
    dataSource: DataSource,
    test: (DataSource) -> T,
): T {
    val config = HikariConfig().apply {
        this.dataSource = dataSource
        maximumPoolSize = 4
        minimumIdle = 0
        connectionTimeout = 10_000
    }
    return HikariDataSource(config).use { pool ->
        test(pool)
    }
}
