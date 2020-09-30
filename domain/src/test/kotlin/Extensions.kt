import com.nhaarman.mockitokotlin2.argThat
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.internal.stubbing.answers.DoesNothing.doesNothing
import org.mockito.stubbing.OngoingStubbing

/**
 * Using kotest matchers because of better error messages
 */
inline fun <reified T : Any> argShouldBe(expected: T): T {
    return argThat {
        this shouldBe expected
        true
    }
}

inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return argThat {
        predicate(this)
        true
    }
}
/**
 * Using T.() instead of T (this instead of it), to be able to overload.
 */
inline fun <reified T : Any> argShouldBe(noinline expectedPredicate: T.() -> T): T {
    return argThat {
        this shouldBe expectedPredicate(this)
        true
    }
}

fun <T> OngoingStubbing<T>.doNothing(): OngoingStubbing<T> {
    return thenAnswer(doesNothing())
}

@Suppress("unused")
infix fun <Arg1, T> OngoingStubbing<T>.doAnswer(predicate: (Arg1) -> T): OngoingStubbing<T> {
    return thenAnswer { invocation ->
        predicate(invocation!!.getArgument(0))
    }
}

infix fun <T, Arg1, Arg2> OngoingStubbing<T>.doAnswer(predicate: (Arg1, Arg2) -> T): OngoingStubbing<T> {
    return thenAnswer { invocation ->
        predicate(invocation!!.getArgument(0), invocation.getArgument(1))
    }
}
