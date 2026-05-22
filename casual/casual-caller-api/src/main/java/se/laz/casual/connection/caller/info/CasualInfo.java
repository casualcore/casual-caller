package se.laz.casual.connection.caller.info;

import java.util.List;

public interface CasualInfo
{
    List<Service> getServices();
    void discoverService(String service);
}
