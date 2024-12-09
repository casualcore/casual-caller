package se.laz.casual.connection.caller.config

import com.github.stefanbirkner.systemlambda.SystemLambda
import spock.lang.Specification

class ConfigurationTest extends Specification
{
   def 'builder defaults'()
   {
      when:
      def config = Configuration.builder().build()
      then:
      config.getJndiSearchRoot() == Configuration.DEFAULT_JNDI_SEARCH_ROOT
      config.getRouteFileName() == Optional.empty()
      config.getTopologyChangeDelayMillis() == Long.parseLong(Configuration.DEFAULT_TOPOLOGY_CHANGED_DELAY)
      config.getValidationIntervalMillis() == Integer.parseInt(Configuration.DEFAULT_VALIDATION_INTERVAL_MILLIS)
      config.isTransactionStickyEnabled() == Boolean.parseBoolean(Configuration.DEFAULT_TRANSACTION_STICKY)
   }

   def 'builder values'()
   {
      given:
      def jndiSearchRoot = 'jndi/foo'
      def routeFilename = 'my-routes.json'
      def topologyChangeDelay = 1200L
      def transactionStickyEnabled = true
      def validationInterval = 1000
      when:
      def config  = Configuration.builder()
              .withJndiSearchRoot(jndiSearchRoot)
              .withRouteFilename(routeFilename)
              .withTopologyChangeDelayMillis(topologyChangeDelay)
              .withValidationIntervalMillis(validationInterval)
              .withTransactionStickyEnabled(transactionStickyEnabled)
              .build()
      then:
      config.getJndiSearchRoot() == jndiSearchRoot
      config.getRouteFileName() == Optional.of(routeFilename)
      config.getTopologyChangeDelayMillis() == topologyChangeDelay
      config.getValidationIntervalMillis() == validationInterval
      config.isTransactionStickyEnabled() == transactionStickyEnabled
   }

   def 'from env vars'()
   {
      given:
      Configuration actual
      def expected  = Configuration.builder()
              .withJndiSearchRoot(jndiSearchRoot)
              .withRouteFilename(routeFilename)
              .withTopologyChangeDelayMillis(null != topologyChangeDelay ? Long.parseLong(topologyChangeDelay) : null)
              .withValidationIntervalMillis(null != validationInterval ? Integer.parseInt(validationInterval) : null)
              .withTransactionStickyEnabled(null != transactionStickyEnabled ? Boolean.parseBoolean(transactionStickyEnabled) : null)
              .build()
      expect:
      SystemLambda.withEnvironmentVariable(Configuration.CASUAL_CALLER_CONNECTION_FACTORY_JNDI_SEARCH_ROOT_ENV_NAME, jndiSearchRoot)
              .and(Configuration.CASUAL_CALLER_VALIDATION_INTERVAL_ENV_NAME, validationInterval)
              .and(Configuration.CASUAL_CALLER_TRANSACTION_STICKY_ENV_NAME, transactionStickyEnabled)
              .and(Configuration.CASUAL_CALLER_TOPOLOGY_CHANGED_DELAY_ENV_NAME, topologyChangeDelay)
              .and(Configuration.ROUTE_FILE_ENV_NAME, routeFilename)
              .execute {
                 actual = Configuration.builder().build()
                 actual == expected
              }
      where:
      jndiSearchRoot || routeFilename || topologyChangeDelay || validationInterval || transactionStickyEnabled
      'jndi'         || 'routes.json' || '100'               || '200'              || 'true'
      null           || 'routes.json' || '100'               || '200'              || 'true'
      'jndi'         || null          || '100'               || '200'              || 'true'
      'jndi'         || 'routes.json' || null                || '200'              || 'true'
      'jndi'         || 'routes.json' || '100'               || null               || 'true'
      'jndi'         || 'routes.json' || '100'               || '200'              || null
   }

}
