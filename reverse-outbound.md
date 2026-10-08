# Reverse outbound support in casual-caller

This document explains how `casual-caller` handles reverse outbound connections established by remote Enterprise Information System (EIS) instances.

## Overview

For standard outbound operations, `casual-caller` discovers connection factories bound in JNDI, performs domain discovery through each factory, and routes service calls to matching endpoints.

For reverse outbound operations, the EIS initiates the TCP connection to `casual-jca`. After the connection is established, `casual-jca` uses it as an outbound connection. Applications that use `casual-caller` can use reverse outbound targets with the following features:

* Per-instance domain discovery
* Service routing and priority failover
* Transactional stickiness and conversations
* Dynamic addition and removal of EIS instances

## Architecture and concepts

```text
                  +-------------------------------------------------------------+
                  |                     Application server                      |
                  |                                                             |
                  |   [ JNDI: java:/eis/casualReverse ] (base pool, unpinned)   |
                  +------------------------------+------------------------------+
                                                 |
                                      1. Classifies base pool
                                      2. Hides base entry
                                      3. Creates virtual entries
                                                 |
                  +------------------------------v------------------------------+
                  |                        casual-caller                        |
                  |                                                             |
                  |   +--------------------------+  +------------------------+  |
                  |   | Virtual entry:           |  | Virtual entry:         |  |
                  |   | java:/eis/casualReverse  |  | java:/eis/casualReverse|  |
                  |   | [Domain-A-UUID]          |  | [Domain-B-UUID]        |  |
                  |   +------------+-------------+  +------------+-----------+  |
                  |                |                             |              |
                  |    Domain discovery and cache    Domain discovery and cache |
                  +----------------|-----------------------------|--------------+
                                   |                             |
                                   v                             v
                       +-----------------------+     +-----------------------+
                       |   EIS instance A      |     |   EIS instance B      |
                       |   (Domain A)          |     |   (Domain B)          |
                       +-----------------------+     +-----------------------+
```

### Base pools and domain-pinned virtual entries

A reverse outbound connection factory configured in JNDI is the base pool. Multiple EIS instances can connect through the same reverse outbound configuration, so an unpinned connection from the base pool could use any connected instance.

To route calls to a specific instance, `casual-caller` performs the following actions:

1. It detects that the connection factory uses a reverse outbound pool and retains the base entry only as an internal factory for virtual entries. It never returns the base entry for application calls.
2. It calls `CasualConnectionFactory.getDomainIds()` and creates one domain-pinned virtual entry for each connected domain. Each entry has the name `<base-jndi-name>[<domain-id>]`.
3. It wraps the base factory in a `DomainIdPinnedConnectionFactory`. The wrapper supplies `CasualRequestInfo.of(domainId)` when it allocates a connection.

Only the virtual entries participate in discovery and caching. The base entry does not enter the service or queue caches.

## Dynamic lifecycle management

During each validation cycle, configured by `CASUAL_CALLER_VALIDATION_INTERVAL`, `casual-caller` synchronizes its virtual entries with the domains reported by each reverse outbound connection factory.

| Lifecycle event | `casual-caller` action |
| :--- | :--- |
| A domain connects | Creates a virtual entry, discovers its services and queues, populates the caches, and registers a topology observer. |
| A domain starts disconnecting | Connection allocation through its pinned factory fails. An application call can fail over to another factory while acquiring a connection. |
| A disconnected domain disappears from `getDomainIds()` | Invalidates and removes the virtual entry during the next validation cycle, then purges its cached services and queues. |
| All domains disconnect | Removes all virtual entries during validation. Calls for services available only through those entries return `TPENOENT` until a domain reconnects and discovery completes. |

After a service invocation begins, an invocation failure propagates to the application without retrying another connection factory in the same transaction. Subsequent calls use the remaining virtual entries.

## Application server configuration

Configure the reverse connection factory as a pooled connection factory in your application server. Set `networkConnectionPoolName` to the name of the corresponding reverse outbound configuration in `casual-jca`.

```xml
<connection-definition class-name="se.laz.casual.jca.CasualManagedConnectionFactory"
                       jndi-name="java:/eis/casualReverse"
                       pool-name="casualReversePool">
    <config-property name="hostName">reverse</config-property>
    <config-property name="portNumber">0</config-property>
    <config-property name="networkConnectionPoolName">myReverseOutbound</config-property>
</connection-definition>
```

The reverse pool ignores `hostName` and `portNumber` because the EIS establishes the physical connection. The `networkConnectionPoolName` value must be nonblank and must match the reverse outbound configuration name. You can omit `networkConnectionPoolSize`; if you specify it, the reverse pool ignores it.

### Size the application server pool

A single connection definition backs all domain-pinned virtual entries for that reverse outbound pool. A managed connection pinned to one domain cannot serve a request for another domain.

Configure the application server pool as follows:

* Set `min-pool-size` to `0` so that the application server does not allocate unpinned managed connections before an EIS connects.
* Set `initial-pool-size` to `0` for the same reason.
* Set `max-pool-size` to at least the expected number of concurrent calls across all connected domains, with capacity for connection churn.
* Enable background validation so that the application server removes managed connections pinned to disconnected domains between calls.

## Observability and troubleshooting

### Log messages

When a domain connects, `casual-caller` logs the following message at `INFO` level:

```text
INFO: reverse inbound instance connected, adding entry: java:/eis/casualReverse[4f8b...89a1]
```

When validation removes a disconnected domain, `casual-caller` logs the following message at `INFO` level:

```text
INFO: reverse inbound instance gone, removing entry: java:/eis/casualReverse[4f8b...89a1]
```

### Inspect entries through JMX

The `validPools`, `invalidPools`, `poolsCheckedForService`, and `poolsContainingService` JMX operations identify virtual entries by their pinned names, such as `java:/eis/casualReverse[<domain-id>]`. The operations do not return the base entry, such as `java:/eis/casualReverse`.
