package se.laz.casual.connection.caller.administration

import se.laz.casual.api.queue.QueueInfo
import se.laz.casual.api.service.ServiceDetails
import se.laz.casual.connection.caller.Cache
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.connection.caller.ConnectionFactoryEntryStore
import se.laz.casual.connection.caller.ConnectionFactoryLookup
import se.laz.casual.connection.caller.ConnectionFactoryProducer
import se.laz.casual.connection.caller.TransactionLess
import se.laz.casual.connection.caller.administration.model.Configuration
import se.laz.casual.connection.caller.administration.model.Queue
import se.laz.casual.connection.caller.administration.model.Service
import se.laz.casual.connection.caller.administration.model.ServiceConnection
import se.laz.casual.connection.caller.administration.model.ServiceInfo
import se.laz.casual.network.messages.domain.TransactionType
import spock.lang.Shared
import spock.lang.Specification

class AdministrationServiceTest extends Specification {

  @Shared
  ConnectionFactoryEntryStore connectionFactoryEntryStoreMock

  @Shared
  Cache cacheMock

  @Shared
  ConnectionFactoryLookup connectionFactoryLookupMock

  @Shared
  TransactionLess transactionLessMock

  @Shared
  AdministrationService administrationService

  def 'testGetConfiguration'() {
    given:
    se.laz.casual.connection.caller.config.Configuration defaultConfig =
        se.laz.casual.connection.caller.config.Configuration.fromEnvOrDefaults()
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    Configuration configuration = administrationService.getConfiguration()
    then:
    configuration.jndiSearchRoot() == defaultConfig.jndiSearchRoot
    configuration.validationIntervalMillis() == defaultConfig.validationIntervalMillis
    configuration.transactionStickyEnabled() == defaultConfig.transactionStickyEnabled
    configuration.topologyChangeDelayMillis() == defaultConfig.topologyChangeDelayMillis
    configuration.routeFileName() == defaultConfig.routeFileName.orElse(null)
  }

  def 'testGetQueue'() {
    given:
    connectionFactoryEntryStoreMock = Mock(ConnectionFactoryEntryStore.class) {
      get() >> List.of(ConnectionFactoryEntry.of(ConnectionFactoryProducer.of("testJndi")))
    }
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    List<ServiceConnection> c = administrationService.getConnections()
    then:
    c.size() == 1
    c.get(0).jndiName() == "testJndi"
    c.get(0).valid()
  }

  def 'testGetServices'() {
    given:
    List<String> cachedServices = List.of("s1", "s2")
    cacheMock = Mock(Cache.class) {
      getServices() >> cachedServices
    }
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    List<String> services = administrationService.getServices()
    then:
    services == cachedServices
  }

  def 'testGetService'() {
    given:
    connectionFactoryLookupMock = Mock(ConnectionFactoryLookup.class) {
      get("s1") >> List.of(ConnectionFactoryEntry.of(ConnectionFactoryProducer.of("testJndi")))
    }
    transactionLessMock = Mock(TransactionLess.class) {
      serviceDetails(*_) >> List.of(ServiceDetails.createBuilder()
          .withName("s1")
          .withCategory("c1")
          .withHops(1)
          .withTimeout(1000)
          .withTransactionType(TransactionType.JOIN)
          .build())
    }
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    ServiceInfo serviceInfo = administrationService.getService("s1")
    then:
    serviceInfo.services().size() == 1
    Service s = serviceInfo.services().get(0)
    s.name() == "s1"
    s.category() == "c1"
    s.hops() == 1
    s.timeout() == 1000
    s.transactionType() == TransactionType.JOIN
    s.serviceConnection().jndiName() == "testJndi"
    s.serviceConnection().valid()
  }

  def 'testGetQueues'() {
    given:
    List<String> cachedQueues = List.of("q1", "q2")
    cacheMock = Mock(Cache.class) {
      getQueues() >> cachedQueues
    }
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    List<String> queues = administrationService.getQueues()
    then:
    queues == cachedQueues
  }

  def 'testGetQueue'() {
    given:
    connectionFactoryLookupMock = Mock(ConnectionFactoryLookup.class) {
      get(QueueInfo.of("q1")) >> Optional.of(ConnectionFactoryEntry.of(ConnectionFactoryProducer.of("testJndi")))
    }
    administrationService =
        new AdministrationService(connectionFactoryEntryStoreMock, cacheMock, connectionFactoryLookupMock, transactionLessMock)
    when:
    Queue queue = administrationService.getQueue("q1")
    then:
    queue.name() == "q1"
    queue.queueConnection().jndiName() == "testJndi"
    queue.queueConnection().valid()
  }
}