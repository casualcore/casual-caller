package se.laz.casual.connection.caller.config

import spock.lang.Specification
import com.github.stefanbirkner.systemlambda.SystemLambda

class ConfigurationServiceTest extends Specification
{
   def 'from file'()
   {
      given:
      def expected  = Configuration.builder()
              .withJndiSearchRoot(jndiSearchRoot)
              .withRouteFilename(routeFilename)
              .withTopologyChangeDelayMillis(topologyChangeDelay)
              .withValidationIntervalMillis(validationInterval)
              .withTransactionStickyEnabled(transactionStickyEnabled)
              .build()
      expect:
      SystemLambda.withEnvironmentVariable(ConfigurationService.CASUAL_CALLER_CONFIG_FILE_ENV_NAME, 'src/test/resources/' + file)
      .execute {
         def actual = new ConfigurationService().getConfiguration()
         actual == expected
      }
      where:
      file                              || jndiSearchRoot || routeFilename  || topologyChangeDelay || validationInterval || transactionStickyEnabled
      'casual-caller-config-all.json'   || 'hello'        || 'foo.json'     || 5000                || 9001               || true
      'casual-caller-config-empty.json' || null           || null           || null                || null               || null
   }
}
