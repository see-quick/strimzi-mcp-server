package io.seequick.mcp.tool;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Generic repository for Strimzi Kubernetes resources.
 * Provides common CRUD operations with filtering by namespace and cluster label.
 *
 * @param <T>     The resource type
 * @param <TList> The resource list type
 */
public class StrimziResourceRepository<T extends HasMetadata, TList extends KubernetesResourceList<T>> {

    private final KubernetesClient client;
    private final Class<T> resourceClass;
    private final Class<TList> listClass;

    public StrimziResourceRepository(KubernetesClient client, Class<T> resourceClass, Class<TList> listClass) {
        this.client = client;
        this.resourceClass = resourceClass;
        this.listClass = listClass;
    }

    /**
     * Lists resources with optional namespace and cluster label filtering.
     *
     * @param namespace    Optional namespace (null for all namespaces)
     * @param clusterLabel Optional cluster label value (null for no filtering)
     * @return The list of matching resources
     */
    public TList list(String namespace, String clusterLabel) {
        return list(namespace, StrimziLabels.CLUSTER, clusterLabel);
    }

    /**
     * Lists resources with optional namespace and custom label filtering.
     *
     * @param namespace  Optional namespace (null for all namespaces)
     * @param labelKey   The label key to filter by
     * @param labelValue Optional label value (null for no filtering)
     * @return The list of matching resources
     */
    public TList list(String namespace, String labelKey, String labelValue) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = client.resources(resourceClass, listClass).inNamespace(namespace);
            if (labelValue != null && !labelValue.isEmpty()) {
                return resource.withLabel(labelKey, labelValue).list();
            }
            return resource.list();
        } else {
            var resource = client.resources(resourceClass, listClass).inAnyNamespace();
            if (labelValue != null && !labelValue.isEmpty()) {
                return resource.withLabel(labelKey, labelValue).list();
            }
            return resource.list();
        }
    }

    /**
     * Gets a single resource by namespace and name.
     *
     * @param namespace The namespace
     * @param name      The resource name
     * @return The resource or null if not found
     */
    public T get(String namespace, String name) {
        return client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    /**
     * Creates a resource in the specified namespace.
     *
     * @param namespace The namespace
     * @param resource  The resource to create
     * @return The created resource
     */
    public T create(String namespace, T resource) {
        return client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .resource(resource)
                .create();
    }

    /**
     * Deletes a resource by namespace and name.
     *
     * @param namespace The namespace
     * @param name      The resource name
     */
    public void delete(String namespace, String name) {
        client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    /**
     * Checks if a resource exists.
     *
     * @param namespace The namespace
     * @param name      The resource name
     * @return true if the resource exists
     */
    public boolean exists(String namespace, String name) {
        return get(namespace, name) != null;
    }
}
