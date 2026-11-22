package com.springproject.lovable_clone.workspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record WorkspaceProperties(@DefaultValue Preview preview) {

    public record Preview(
            @DefaultValue("local") String mode,
            @DefaultValue("60m") Duration ttl,
            @DefaultValue Local local,
            @DefaultValue Kubernetes kubernetes
    ) {
        public boolean isKubernetes() {
            return "kubernetes".equalsIgnoreCase(mode);
        }
    }

    /**
     * @param host       interface the dev servers bind to
     * @param publicHost host name put in preview URLs (defaults to {@code host})
     */
    public record Local(
            @DefaultValue("./runtime/previews") String root,
            @DefaultValue("127.0.0.1") String host,
            String publicHost,
            @DefaultValue("5200") int portRangeStart,
            @DefaultValue("5299") int portRangeEnd
    ) {
        public String urlHost() {
            if (publicHost != null && !publicHost.isBlank()) return publicHost;
            return "0.0.0.0".equals(host) ? "localhost" : host;
        }

        /** Address used to probe whether a dev server is listening. */
        public String probeHost() {
            return "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        }
    }

    public record Kubernetes(
            @DefaultValue("lovable-previews") String namespace,
            @DefaultValue("app.localhost") String domain,
            @DefaultValue("80") String proxyPort
    ) {}
}
