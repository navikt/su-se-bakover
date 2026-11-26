package no.nav.su.se.bakover.test.persistence

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
class TestConnectionPoolTest(private val dataSource: DataSource) {
    @Test
    fun `pool reuses physical connections and closes them after the application scope`() {
        val tracked = TrackingDataSource(dataSource)
        val borrows = 20
        lateinit var pool: HikariDataSource

        withTestConnectionPool(tracked) { scoped ->
            pool = assertInstanceOf(HikariDataSource::class.java, scoped)
            repeat(borrows) {
                scoped.connection.use { it.isClosed shouldBe false }
            }
            tracked.connections.size.shouldBeLessThan(borrows)
        }

        pool.isClosed shouldBe true
        tracked.connections.forEach { it.isClosed shouldBe true }
    }

    @Test
    fun `pool closes connections and preserves failures during application setup or execution`() {
        val tracked = TrackingDataSource(dataSource)
        val failure = IllegalStateException("Application setup failed")
        lateinit var pool: HikariDataSource

        val thrown = assertThrows(IllegalStateException::class.java) {
            withTestConnectionPool(tracked) { scoped ->
                pool = assertInstanceOf(HikariDataSource::class.java, scoped)
                scoped.connection.use { it.isClosed shouldBe false }
                throw failure
            }
        }

        assertSame(failure, thrown)
        pool.isClosed shouldBe true
        tracked.connections.forEach { it.isClosed shouldBe true }
    }

    private class TrackingDataSource(
        private val delegate: DataSource,
    ) : DataSource by delegate {
        val connections = ConcurrentLinkedQueue<Connection>()

        override fun getConnection(): Connection = delegate.connection.also { connections.add(it) }
    }
}
