# Strimzi MCP Server

> **Note:** This is not an official [Strimzi](https://strimzi.io/) project. It is an independent, community-driven tool.

An MCP (Model Context Protocol) server for interacting with [Strimzi](https://strimzi.io/) Kafka on Kubernetes.
Enables AI assistants like Claude to manage and troubleshoot Strimzi resources.

## Features

### Kafka Cluster Management
- `list_kafkas` - List Kafka clusters across namespaces
- `get_kafka_status` - Get detailed cluster status and conditions

### Topic Operator
- `list_topics` - List KafkaTopic resources
- `describe_topic` - Get detailed topic info (spec, status, config)
- `create_topic` - Create new KafkaTopic resources
- `delete_topic` - Delete KafkaTopic resources
- `update_topic_config` - Update topic partitions or configuration
- `get_unready_topics` - Find topics with issues
- `get_topic_operator_status` - Check entity-operator pod health

### User Operator
- `list_users` - List KafkaUser resources
- `describe_user` - Get user details (authentication, ACLs, quotas)
- `create_user` - Create new KafkaUser with authentication config
- `delete_user` - Delete KafkaUser resources
- `get_user_credentials` - Get credentials from generated Secret
- `get_user_operator_status` - Check user-operator container health

### Cluster Operator
- `list_node_pools` - List KafkaNodePool resources
- `describe_node_pool` - Get node pool details (roles, node IDs, storage)
- `list_kafka_connects` - List KafkaConnect clusters
- `describe_kafka_connect` - Get detailed KafkaConnect info (plugins, build config)
- `list_connectors` - List KafkaConnector resources
- `describe_connector` - Get connector details (config, tasks, status)
- `list_rebalances` - List KafkaRebalance resources (Cruise Control)
- `describe_rebalance` - Get rebalance details (optimization proposal, progress)
- `get_cluster_operator_status` - Check Cluster Operator deployment health

### Kafka MirrorMaker 2
- `list_mirrormaker2s` - List KafkaMirrorMaker2 resources
- `describe_mirrormaker2` - Get MM2 details (source/target clusters, connectors)

### Kafka Bridge
- `list_bridges` - List KafkaBridge resources (HTTP access)
- `describe_bridge` - Get bridge details (HTTP config, producer/consumer settings)

## Build

```bash
mvn package -DskipTests
```

## Usage

Add to Claude Code settings (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "strimzi": {
      "command": "java",
      "args": ["-jar", "/path/to/strimzi-mcp-server-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

The server uses your local kubeconfig (`~/.kube/config`) to connect to the Kubernetes cluster.

## Requirements

- Java 21+
- Kubernetes cluster with Strimzi installed
- Valid kubeconfig

## License

Apache License 2.0
