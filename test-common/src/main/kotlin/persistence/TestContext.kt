package no.nav.su.se.bakover.test.persistence

import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import javax.sql.DataSource

/**
 * Don't run setup-code in here
 */
fun withTestContext(dataSource: DataSource, expectedConnections: Int = 1, test: (spiedDataSource: DataSource) -> Unit) {
    val spiedDataSource = spy(dataSource as PGSimpleDataSource)
    val resultCaptor = ResultCaptor<Connection>()
    Mockito.doAnswer(resultCaptor).whenever(spiedDataSource).connection
    test(spiedDataSource)
    resultCaptor.result.size shouldBe expectedConnections
    resultCaptor.result.forEach {
        it!!.isClosed shouldBe true
    }
}

private class ResultCaptor<T> : Answer<T> {
    var result: List<T?> = listOf()
        private set

    @Suppress("UNCHECKED_CAST")
    override fun answer(invocationOnMock: InvocationOnMock): T? {
        return (invocationOnMock.callRealMethod() as T).also {
            result = result + it
        }
    }
}
