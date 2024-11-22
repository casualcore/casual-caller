package se.laz.casual.connection.caller.services

import spock.lang.Specification

class ServiceRouteReaderTest extends Specification
{
   def'reading routes'()
   {
      when:
      ServiceRoutes serviceRoutes = ServiceRouteReader.load('src/test/resources/service-routes.json')
      then:
      !serviceRoutes.isEmpty()
      !serviceRoutes.getRoute('test-service').isEmpty()
      !serviceRoutes.getRoute('another-test-service').isEmpty()
   }
   def'non existing file'()
   {
      when:
      ServiceRouteReader.load('src/test/resources/does-not-exist.json')
      then:
      thrown(ServiceRouteReaderException)
   }
}
