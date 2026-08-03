package com.mdframe.forge.plugin.system.strategy;

import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.keyexchange.RsaKeyPairHolder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsernamePasswordAuthStrategyTest {

    @Test
    void shouldRejectPasswordWhenRsaDecryptionFailsInCryptoMode() {
        UsernamePasswordAuthStrategy strategy = new UsernamePasswordAuthStrategy();
        RsaKeyPairHolder keyPairHolder = mock(RsaKeyPairHolder.class);
        when(keyPairHolder.decryptByPrivateKey("plain-password"))
                .thenThrow(new IllegalArgumentException("invalid ciphertext"));
        CryptoProperties properties = new CryptoProperties();
        properties.setEnabled(true);
        ReflectionTestUtils.setField(strategy, "rsaKeyPairHolder", keyPairHolder);
        ReflectionTestUtils.setField(strategy, "cryptoProperties", properties);

        assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(strategy, "decryptPasswordIfNeeded", "plain-password"));
    }

    @Test
    void shouldAllowPlainPasswordOnlyWhenCryptoIsDisabled() {
        UsernamePasswordAuthStrategy strategy = new UsernamePasswordAuthStrategy();
        CryptoProperties properties = new CryptoProperties();
        properties.setEnabled(false);
        ReflectionTestUtils.setField(strategy, "cryptoProperties", properties);

        String password = ReflectionTestUtils.invokeMethod(strategy, "decryptPasswordIfNeeded", "plain-password");

        assertEquals("plain-password", password);
    }
}
