package com.wcs.drawing.game.draw.with.music.`fun`

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class BillingProofTest {
    private val pair=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
    private val publicKey=Base64.getEncoder().encodeToString(pair.public.encoded)
    private val payload="purchase-test-payload"
    private fun signed(value:String)=Signature.getInstance("SHA1withRSA").run {
        initSign(pair.private);update(value.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }
    @Test fun acceptsAuthenticSignature() {assertTrue(BillingProof.verify(publicKey,payload,signed(payload)))}
    @Test fun rejectsChangedPayload() {assertFalse(BillingProof.verify(publicKey,payload+"tampered",signed(payload)))}
    @Test fun rejectsDifferentKey() {
        val other=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        assertFalse(BillingProof.verify(Base64.getEncoder().encodeToString(other.public.encoded),payload,signed(payload)))
    }
    @Test fun missingOrMalformedConfigurationFailsClosed() {
        assertFalse(BillingProof.validKey(""));assertFalse(BillingProof.validKey("not-a-key"))
        assertFalse(BillingProof.verify("",payload,signed(payload)))
        assertFalse(BillingProof.verify(publicKey,payload,"not-a-signature"))
    }
}
