# Reverse Outbound Support in Casual Caller

This document explains how `casual-caller` handles **Reverse Outbound** connections established by remote Enterprise Information System (EIS) instances.

---

## Overview

In standard outbound operations, `casual-caller` discovers connection factories bound in JNDI, performs domain discovery over each factory, and routes service calls to matching endpoints.

In **Reverse Outbound**, the EIS initiates the TCP connection to `casual-jca`. Once connected, `casual-jca` switches roles to initiate the outbound handshake. To an application using `casual-caller`, reverse outbound connections operate transparently as standard outbound targets with full support for:

* Automatic per-instance domain discovery
* Service routing and priority failover
* Transactional stickiness and conversations
* Dynamic addition and removal of EIS instances at runtime

---

## Architecture and Concepts

```
                  +-------------------------------------------------------------+
                  |                     Application Server                      |
                  |                                                             |
                  |   [ JNDI: java:/eis/casualReverse ] (Base Pool - Unpinned)  |
                  +------------------------------+------------------------------+
                                                 |
                                     (1) Classifies Base Pool
                                     (2) Hides Base Entry
                                     (3) Creates Virtual Entries
                                                 |
                  +------------------------------v------------------------------+
                  |                        casual-caller                        |
                  |                                                             |
                  |   +--------------------------+  +------------------------+  |
                  |   | Virtual Entry:           |  | Virtual Entry:         |  |
                  |   | java:/eis/casualReverse  |  | java:/eis/casualReverse|  |
                  |   | [Domain-A-UUID]          |  | [Domain-B-UUID]        |  |
                  |   +------------+-------------+  +------------+-----------+  |
                  |                |                             |              |
                  |   (Domain Discovery & Cache)    (Domain Discovery & Cache)  |
                  +----------------|-----------------------------|--------------+
                                   |                             |
                                   v                             v
                       +-----------------------+     +-----------------------+
                       |   EIS Instance A      |     |   EIS Instance B      |
                       |   (Domain A)          |     |   (Domain B)          |
                       +-----------------------+     +-----------------------+
```

### Base Pools vs. Domain-Pinned Virtual Entries

A Reverse Outbound connection factory configured in JNDI (the **Base Pool**) represents a generic listener. Because multiple distinct EIS instances can connect to the same listening port, an unpinned base connection could arbitrarily route to any connected instance.

To guarantee deterministic routing:

1. **Base Pool Hiding:** `casual-caller` automatically detects that a connection factory is backed by a reverse pool and **never serves the base entry directly** for application calls.
2. **Virtual Entry Splitting:** `casual-caller` inspects the connected remote domains and creates a **Domain-Pinned Virtual Entry** for each connected instance:
   $$\text{Virtual Entry Name} = \text{baseJndiName} + \text{"["} + \text{DomainId} + \text{"]"}$$
3. **Strict Domain Pinning:** Each virtual entry wraps the base factory in a `DomainIdPinnedConnectionFactory` that automatically attaches `CasualRequestInfo.of(domainId)` on all allocation requests.

---

## Dynamic Lifecycle Management

`casual-caller` continuously synchronizes its internal state with connected EIS instances during the validation loop (configured by `CASUAL_CALLER_VALIDATION_INTERVAL`):

| Lifecycle Event | Action Taken by `casual-caller` |
| :--- | :--- |
| **New EIS Instance Connects** | 1. Detects new `DomainId` from `connection.getPoolDomainIds()`.<br>2. Instantiates a new virtual `ConnectionFactoryEntry`.<br>3. Runs service/queue discovery and populates the cache.<br>4. Registers a topology observer for the new instance. |
| **EIS Instance Disconnects** | 1. Detects missing `DomainId`.<br>2. Marks the virtual entry as `invalid`.<br>3. Purges all cached services and queues mapped to that virtual entry.<br>4. In-flight and subsequent calls fail over to surviving instances. |
| **Initial Base Pool Classification** | Purges any stale/negative lookup state recorded under the base JNDI name prior to the first EIS connection. |
| **Total Outage (All Instances Gone)** | Virtual entries are purged. Application calls for services provided only by that reverse pool immediately fail with `TPENOENT` until an EIS reconnects. |

---

## Application Server Configuration

Configure the reverse connection factory like a standard pooled connection factory in your application server:

```xml
<connection-definition class-name="se.laz.casual.jca.CasualManagedConnectionFactory"
                       jndi-name="java:/eis/casualReverse"
                       pool-name="casualReversePool">
    <config-property name="hostName">reverse</config-property>
    <config-property name="portNumber">0</config-property>
    <config-property name="networkConnectionPoolName">myReverseOutbound</config-property>
    <config-property name="networkConnectionPoolSize">1</config-property>
</connection-definition>
```

> **Note:** For reverse pools, `hostName`, `portNumber`, and `networkConnectionPoolSize` are ignored by `casual-jca`. The pool accepts connections dynamically as EIS instances connect.

### Pool Sizing Best Practices

Because a single connection definition in the application server backs $N$ virtual domain-pinned pools, apply the following pool sizing rules:

* **`min-pool-size = 0`:** Prevents the application server from pre-allocating unpinned managed connections before instances connect.
* **`max-pool-size`:** Set this to a generous value (at least equal to the maximum concurrent calls expected across all connected instances combined, plus headroom for churn).
* **Enable Background Validation:** Ensures that the application server destroys managed connections pinned to disconnected instances between validation cycles.

---

## Observability & Troubleshooting

### Log Messages

During normal operations, `casual-caller` logs reverse outbound lifecycle transitions at `INFO` level:

* **Instance Connected:**
  ```text
  INFO: reverse inbound instance connected, adding entry: java:/eis/casualReverse[4f8b...89a1]
  ```
* **Instance Disconnected:**
  ```text
  INFO: reverse instance gone, removing entry: java:/eis/casualReverse[4f8b...89a1]
  ```

### Inspection via API / JMX

* Virtual entries appear in `casual-caller` metrics and discovery dumps with their pinned name: `java:/eis/casualReverse[<domain-uuid>]`.
* The base entry `java:/eis/casualReverse` is excluded from active service route tables.
