package se.laz.casual.connection.caller.administration.model;

import java.util.List;

public record ServiceInfo(List<Service> services, List<ServiceConnection> connections) {
}
