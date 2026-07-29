package com.mdframe.forge.starter.collaboration.provider;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.CollaborationConnector;
import com.mdframe.forge.starter.collaboration.connector.LoginConnector;
import com.mdframe.forge.starter.collaboration.connector.MessageConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageRequest;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageResult;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CollaborationProviderRegistry 注册与失败关闭行为测试
 */
class CollaborationProviderRegistryTest {

    private record FakeProvider(String platform, Set<CollaborationCapability> capabilities)
            implements CollaborationProvider {
    }

    private static final class FakeLoginConnector implements LoginConnector {
        private final String platform;

        private FakeLoginConnector(String platform) {
            this.platform = platform;
        }

        @Override
        public String platform() {
            return platform;
        }

        @Override
        public String buildAuthorizeUrl(CollaborationExecutionContext context, String state, String redirectUri) {
            return "https://fake/authorize?state=" + state;
        }

        @Override
        public VerifiedSocialIdentity exchangeIdentity(CollaborationExecutionContext context, String authCode) {
            return null;
        }
    }

    private static final class FakeMessageConnector implements MessageConnector {
        private final String platform;

        private FakeMessageConnector(String platform) {
            this.platform = platform;
        }

        @Override
        public String platform() {
            return platform;
        }

        @Override
        public ProviderMessageResult send(ProviderMessageRequest request, CollaborationExecutionContext context) {
            return new ProviderMessageResult(null, List.of());
        }
    }

    @Test
    void partialCapabilityProviderIsAllowed() {
        CollaborationProviderRegistry registry = new CollaborationProviderRegistry(
                List.of(new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN))),
                List.of(new FakeLoginConnector("fake")));

        assertThat(registry.platforms()).containsExactly("fake");
        assertThat(registry.supports("fake", CollaborationCapability.LOGIN)).isTrue();
        assertThat(registry.supports("fake", CollaborationCapability.MESSAGE)).isFalse();
        assertThat(registry.requireConnector("fake", CollaborationCapability.LOGIN, LoginConnector.class))
                .isInstanceOf(FakeLoginConnector.class);
    }

    @Test
    void missingCapabilityFailsExplicitly() {
        CollaborationProviderRegistry registry = new CollaborationProviderRegistry(
                List.of(new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN))),
                List.of(new FakeLoginConnector("fake")));

        assertThatThrownBy(() -> registry.requireConnector("fake", CollaborationCapability.MESSAGE, MessageConnector.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持该能力");
    }

    @Test
    void unknownPlatformFailsExplicitly() {
        CollaborationProviderRegistry registry = new CollaborationProviderRegistry(List.of(), List.of());

        assertThatThrownBy(() -> registry.requireProvider("wecom"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void duplicateProviderFailsClosed() {
        List<CollaborationProvider> providers = List.of(
                new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN)),
                new FakeProvider("FAKE", Set.of(CollaborationCapability.MESSAGE)));

        assertThatThrownBy(() -> new CollaborationProviderRegistry(providers, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册 Provider");
    }

    @Test
    void duplicateConnectorForSameCapabilityFailsClosed() {
        List<CollaborationProvider> providers =
                List.of(new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN)));
        List<CollaborationConnector> connectors = List.of(
                new FakeLoginConnector("fake"),
                new FakeLoginConnector("fake"));

        assertThatThrownBy(() -> new CollaborationProviderRegistry(providers, connectors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册 Connector");
    }

    @Test
    void connectorWithoutProviderFailsClosed() {
        assertThatThrownBy(() -> new CollaborationProviderRegistry(
                List.of(),
                List.of(new FakeLoginConnector("fake"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册 Provider");
    }

    @Test
    void connectorCapabilityNotDeclaredByProviderFailsClosed() {
        assertThatThrownBy(() -> new CollaborationProviderRegistry(
                List.of(new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN))),
                List.of(new FakeLoginConnector("fake"), new FakeMessageConnector("fake"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("能力未在 Provider 中声明");
    }

    @Test
    void declaredCapabilityWithoutConnectorFailsClosed() {
        assertThatThrownBy(() -> new CollaborationProviderRegistry(
                List.of(new FakeProvider("fake",
                        Set.of(CollaborationCapability.LOGIN, CollaborationCapability.MESSAGE))),
                List.of(new FakeLoginConnector("fake"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 Connector 实现");
    }

    @Test
    void connectorTypeMismatchFailsExplicitly() {
        CollaborationProviderRegistry registry = new CollaborationProviderRegistry(
                List.of(new FakeProvider("fake", Set.of(CollaborationCapability.LOGIN))),
                List.of(new FakeLoginConnector("fake")));

        assertThatThrownBy(() -> registry.requireConnector("fake", CollaborationCapability.LOGIN, MessageConnector.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("类型不匹配");
    }
}
