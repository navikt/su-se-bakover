package no.nav.su.se.bakover.common.stubs

import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

object AuthStubCommonConfig {
    private val keypair = generateKeypair()
    val keyId = "key-1234"
    val issuer = "localhost"
    val public = keypair.first
    val private = keypair.second

    private fun generateKeypair(): Pair<RSAPublicKey, RSAPrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)
        val keypair = keyPairGenerator.genKeyPair()
        return keypair.public as RSAPublicKey to keypair.private as RSAPrivateKey
    }
}
