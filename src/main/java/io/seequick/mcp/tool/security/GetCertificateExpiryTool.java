package io.seequick.mcp.tool.security;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Tool to check certificate expiry dates for a Kafka cluster.
 */
public class GetCertificateExpiryTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka cluster"
                    },
                    "warningDays": {
                        "type": "integer",
                        "description": "Show warning if certificate expires within this many days (default: 30)"
                    }
                },
                "required": ["kafkaCluster", "namespace"]
            }
            """;

    public GetCertificateExpiryTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_certificate_expiry";
    }

    @Override
    protected String getDescription() {
        return "Check certificate expiry dates for a Kafka cluster's CA and listener certificates";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            String namespace = getStringArg(args, "namespace");
            int warningDays = getIntArg(args, "warningDays", 30);

            // Verify Kafka cluster exists
            Kafka kafka = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace)
                    .withName(kafkaCluster)
                    .get();

            if (kafka == null) {
                return error("Kafka cluster not found: " + namespace + "/" + kafkaCluster);
            }

            StringBuilder result = new StringBuilder();
            result.append("Certificate Expiry Report for: ").append(namespace).append("/").append(kafkaCluster).append("\n");
            result.append("═".repeat(60)).append("\n\n");

            Instant warningThreshold = Instant.now().plus(warningDays, ChronoUnit.DAYS);
            boolean hasWarnings = false;

            // Check Cluster CA
            String clusterCaSecretName = kafkaCluster + "-cluster-ca-cert";
            Secret clusterCaSecret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(clusterCaSecretName)
                    .get();

            result.append("CLUSTER CA\n");
            result.append("─".repeat(40)).append("\n");
            if (clusterCaSecret != null && clusterCaSecret.getData() != null) {
                String caCrt = clusterCaSecret.getData().get("ca.crt");
                if (caCrt != null) {
                    CertInfo info = parseCertificate(caCrt);
                    if (info != null) {
                        result.append("  Subject: ").append(info.subject).append("\n");
                        result.append("  Not Before: ").append(info.notBefore).append("\n");
                        result.append("  Not After: ").append(info.notAfter);

                        if (info.expiry.isBefore(Instant.now())) {
                            result.append(" ✗ EXPIRED");
                            hasWarnings = true;
                        } else if (info.expiry.isBefore(warningThreshold)) {
                            long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), info.expiry);
                            result.append(" ⚠ Expires in ").append(daysUntilExpiry).append(" days");
                            hasWarnings = true;
                        } else {
                            result.append(" ✓");
                        }
                        result.append("\n");
                    }
                } else {
                    result.append("  ca.crt not found in secret\n");
                }
            } else {
                result.append("  Secret not found\n");
            }
            result.append("\n");

            // Check Clients CA
            String clientsCaSecretName = kafkaCluster + "-clients-ca-cert";
            Secret clientsCaSecret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(clientsCaSecretName)
                    .get();

            result.append("CLIENTS CA\n");
            result.append("─".repeat(40)).append("\n");
            if (clientsCaSecret != null && clientsCaSecret.getData() != null) {
                String caCrt = clientsCaSecret.getData().get("ca.crt");
                if (caCrt != null) {
                    CertInfo info = parseCertificate(caCrt);
                    if (info != null) {
                        result.append("  Subject: ").append(info.subject).append("\n");
                        result.append("  Not Before: ").append(info.notBefore).append("\n");
                        result.append("  Not After: ").append(info.notAfter);

                        if (info.expiry.isBefore(Instant.now())) {
                            result.append(" ✗ EXPIRED");
                            hasWarnings = true;
                        } else if (info.expiry.isBefore(warningThreshold)) {
                            long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), info.expiry);
                            result.append(" ⚠ Expires in ").append(daysUntilExpiry).append(" days");
                            hasWarnings = true;
                        } else {
                            result.append(" ✓");
                        }
                        result.append("\n");
                    }
                } else {
                    result.append("  ca.crt not found in secret\n");
                }
            } else {
                result.append("  Secret not found\n");
            }
            result.append("\n");

            // Summary
            result.append("═".repeat(60)).append("\n");
            if (hasWarnings) {
                result.append("⚠ Warning: Some certificates need attention!\n");
                result.append("Consider rotating certificates before they expire.\n");
            } else {
                result.append("✓ All certificates are valid and not expiring soon.\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error checking certificate expiry: " + e.getMessage());
        }
    }

    private CertInfo parseCertificate(String base64Cert) {
        try {
            byte[] certBytes = Base64.getDecoder().decode(base64Cert);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));

            CertInfo info = new CertInfo();
            info.subject = cert.getSubjectX500Principal().getName();
            info.notBefore = cert.getNotBefore().toString();
            info.notAfter = cert.getNotAfter().toString();
            info.expiry = cert.getNotAfter().toInstant();

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private static class CertInfo {
        String subject;
        String notBefore;
        String notAfter;
        Instant expiry;
    }
}
