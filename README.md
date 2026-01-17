# Strimzi MCP Server

> [!NOTE] 
> This is not an official [Strimzi](https://strimzi.io/) project. It is an independent, community-driven tool.

An MCP (Model Context Protocol) server for interacting with [Strimzi](https://strimzi.io/) Kafka on Kubernetes.
Enables AI assistants like Claude to manage and troubleshoot Strimzi resources.

## Features

**62 tools** for comprehensive Strimzi Kafka management on Kubernetes.

### Kafka Cluster Management
- `list_kafkas` - List Kafka clusters across namespaces
- `get_kafka_status` - Get detailed cluster status and conditions
- `restart_kafka_broker` - Trigger rolling restart via annotation
- `get_kafka_listeners` - List listener addresses for connections
- `scale_node_pool` - Adjust KafkaNodePool replicas

### Topic Operator
- `list_topics` - List KafkaTopic resources
- `describe_topic` - Get detailed topic info (spec, status, config)
- `create_topic` - Create new KafkaTopic resources
- `delete_topic` - Delete KafkaTopic resources
- `update_topic_config` - Update topic partitions or configuration
- `get_unready_topics` - Find topics with issues
- `get_topic_operator_status` - Check entity-operator pod health
- `compare_topic_config` - Compare topic configurations

### User Operator
- `list_users` - List KafkaUser resources
- `describe_user` - Get user details (authentication, ACLs, quotas)
- `create_user` - Create new KafkaUser with authentication config
- `delete_user` - Delete KafkaUser resources
- `get_user_credentials` - Get credentials from generated Secret
- `get_user_operator_status` - Check user-operator container health
- `update_user_acls` - Show/clear ACL rules
- `update_user_quotas` - Modify user quotas
- `list_user_acls` - Display ACLs in readable format

### Cluster Operator
- `list_node_pools` - List KafkaNodePool resources
- `describe_node_pool` - Get node pool details (roles, node IDs, storage)
- `get_cluster_operator_status` - Check Cluster Operator deployment health

### Kafka Connect
- `list_kafka_connects` - List KafkaConnect clusters
- `describe_kafka_connect` - Get detailed KafkaConnect info (plugins, build config)
- `list_connect_plugins` - List available connector plugins
- `list_connectors` - List KafkaConnector resources
- `describe_connector` - Get connector details (config, tasks, status)
- `create_connector` - Create new KafkaConnector
- `delete_connector` - Delete a connector
- `pause_connector` - Pause a connector
- `resume_connector` - Resume a paused connector
- `restart_connector` - Restart a connector
- `update_connector_config` - Update connector configuration

### Cruise Control (Rebalancing)
- `list_rebalances` - List KafkaRebalance resources
- `describe_rebalance` - Get rebalance details (optimization proposal, progress)
- `create_rebalance` - Create KafkaRebalance resource
- `approve_rebalance` - Approve a rebalance proposal
- `stop_rebalance` - Stop/cancel a rebalance
- `refresh_rebalance` - Refresh a rebalance proposal

### Kafka MirrorMaker 2
- `list_mirrormaker2s` - List KafkaMirrorMaker2 resources
- `describe_mirrormaker2` - Get MM2 details (source/target clusters, connectors)
- `create_mirrormaker2` - Create cross-cluster replication

### Kafka Bridge
- `list_bridges` - List KafkaBridge resources (HTTP access)
- `describe_bridge` - Get bridge details (HTTP config, producer/consumer settings)

### Observability
- `get_kafka_logs` - Fetch Kafka broker logs
- `get_operator_logs` - Fetch operator logs
- `get_kafka_events` - List Kubernetes events
- `health_check` - Comprehensive cluster health check
- `describe_kafka_pod` - Pod details and resources

### Security
- `rotate_user_credentials` - Rotate user credentials
- `list_certificates` - List cluster certificates
- `get_certificate_expiry` - Check certificate expiry dates

### Utilities
- `export_resource_yaml` - Export resources as YAML
- `get_strimzi_version` - Get Strimzi/operator versions
- `list_all_resources` - Summary of all Strimzi resources

## Build

```bash
mvn package -DskipTests
```

## Installation

### Option 1: Claude Code Plugin (Recommended)

```bash
/plugin marketplace add see-quick/strimzi-mcp-server
/plugin install strimzi-mcp@see-quick-strimzi-mcp-server
```

The plugin automatically downloads the jar on first use.

### Option 2: Manual

Download from [GitHub Releases](https://github.com/see-quick/strimzi-mcp-server/releases) and configure:

```bash
claude mcp add strimzi -- java -jar /path/to/strimzi-mcp-server.jar
```

## Configuration

The server uses your local kubeconfig (`~/.kube/config`) to connect to the Kubernetes cluster.

## Requirements

- Java 21+
- Kubernetes cluster with Strimzi installed
- Valid kubeconfig

## License

Apache License 2.0
