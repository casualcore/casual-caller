package se.laz.casual.connection.caller.services

import se.laz.casual.connection.caller.config.Configuration
import spock.lang.Specification

class ServiceRoutesTest extends Specification
{
   def 'no route file'()
   {
      when:
      ServiceRoutes routes = ServiceRoutes.of(Configuration.builder().build())
      then:
      routes.isEmpty()
   }

   def 'with route file'()
   {
      given:
      def configFile = 'src/test/resources/service-routes.json'
      def serviceNameOne = 'test-service'
      def serviceNameTwo = 'another-test-service'
      when:
      ServiceRoutes routes = ServiceRoutes.of(Configuration.builder()
              .withRouteFilename(configFile)
              .build())
      then:
      !routes.isEmpty()
      !routes.getRoute(serviceNameOne).isEmpty()
      !routes.getRoute(serviceNameTwo).isEmpty()
      routes.getRoute('does-not-exist').isEmpty()
   }

   def 'with route file with trailing space'()
   {
      given:
      def configFile = 'src/test/resources/service-routes-trailing-space.json'
      def serviceNameOne = 'test-service'
      def serviceNameTwo = 'another-test-service'
      when:
      ServiceRoutes routes = ServiceRoutes.of(Configuration.builder()
              .withRouteFilename(configFile)
              .build())
      then:
      !routes.isEmpty()
      !routes.getRoute(serviceNameOne).isEmpty()
      !routes.getRoute(serviceNameTwo).isEmpty()
      routes.getRoute('does-not-exist').isEmpty()
   }


}
