package no.nav.su.se.bakover.common

import java.util.UUID

open class UUIDFactory {
    open fun newUUID(): UUID = UUID.randomUUID()
    open fun newUUID30(): UUID30 = UUID30.randomUUID()
}
