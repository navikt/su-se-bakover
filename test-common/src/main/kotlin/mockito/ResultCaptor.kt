package no.nav.su.se.bakover.test.external

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class ResultCaptor<T> : Answer<T> {
    var result: List<T?> = listOf()
        private set

    @Suppress("UNCHECKED_CAST")
    override fun answer(invocationOnMock: InvocationOnMock): T? {
        return (invocationOnMock.callRealMethod() as T).also {
            result = result + it
        }
    }
}
