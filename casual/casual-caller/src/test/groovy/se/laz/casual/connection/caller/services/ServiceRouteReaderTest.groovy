package se.laz.casual.connection.caller.services

import spock.lang.Specification

import java.nio.file.Path

class ServiceRouteReaderTest extends Specification
{
   def'reading routes'()
   {
      when:
      ServiceRoutes serviceRoutes = ServiceRouteReader.load(Path.of('src/test/resources/service-routes.json').toAbsolutePath().normalize())
      then:
      !serviceRoutes.isEmpty()
      !serviceRoutes.getRoute('test-service').isEmpty()
      !serviceRoutes.getRoute('another-test-service').isEmpty()
   }
   def'non existing file'()
   {
      when:
      ServiceRouteReader.load(Path.of('src/test/resources/does-not-exist.json').toAbsolutePath().normalize())
      then:
      thrown(ServiceRouteReaderException)
   }
}
