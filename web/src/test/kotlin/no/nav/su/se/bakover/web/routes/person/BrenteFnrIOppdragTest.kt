package no.nav.su.se.bakover.web.routes.person

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test

internal class BrenteFnrIOppdragTest {
    @Test
    fun `banned in preprod should work in prod`() {
        val config = mock<Config> {
            on { isPreprod } doReturn false
        }
        shouldNotThrow<BrentFnrIOppdragPreprod> {
            BrenteFnrIOppdragPreprodValidator(config).assertUbrentFødselsnummerIOppdragPreprod(Fnr("02057512841"))
        }
    }

    @Test
    fun `not banned in preprod should work in prod`() {
        val config = mock<Config> {
            on { isPreprod } doReturn false
        }
        shouldNotThrow<BrentFnrIOppdragPreprod> {
            BrenteFnrIOppdragPreprodValidator(config).assertUbrentFødselsnummerIOppdragPreprod(Fnr("02057512842"))
        }
    }

    @Test
    fun `banned in preprod should not work in preprod`() {
        val config = mock<Config> {
            on { isPreprod } doReturn true
        }
        shouldThrow<BrentFnrIOppdragPreprod> {
            BrenteFnrIOppdragPreprodValidator(config).assertUbrentFødselsnummerIOppdragPreprod(Fnr("02057512841"))
        }
    }

    @Test
    fun `not banned in preprod should work in preprod`() {
        val config = mock<Config> {
            on { isPreprod } doReturn true
        }
        shouldNotThrow<BrentFnrIOppdragPreprod> {
            BrenteFnrIOppdragPreprodValidator(config).assertUbrentFødselsnummerIOppdragPreprod(Fnr("02057512842"))
        }
    }
}
