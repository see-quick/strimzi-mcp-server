package io.seequick.mcp.tool;

/**
 * Constants for Strimzi Kubernetes labels.
 */
public final class StrimziLabels {

    /**
     * The strimzi.io/cluster label used to associate resources with a Kafka cluster.
     */
    public static final String CLUSTER = "strimzi.io/cluster";

    /**
     * The strimzi.io/kind label used to identify the type of Strimzi resource.
     */
    public static final String KIND = "strimzi.io/kind";

    /**
     * The strimzi.io/name label used for resource naming.
     */
    public static final String NAME = "strimzi.io/name";

    private StrimziLabels() {
        // Utility class
    }
}
