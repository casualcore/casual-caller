package se.laz.casual.connection.caller.services;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import se.laz.casual.api.CasualRuntimeException;

import java.io.FileNotFoundException;
import java.io.FileReader;

public final class ServiceRouteReader
{
    private ServiceRouteReader()
    {}
    public static ServiceRoutes load(String filename)
    {
        Constructor constructor = new Constructor(ServiceRoutes.class, new LoaderOptions());
        TypeDescription customTypeDescription = new TypeDescription(ServiceRoutes.class);
        customTypeDescription.addPropertyParameters("routes", Route.class);
        constructor.addTypeDescription(customTypeDescription);
        Yaml yaml = new Yaml(constructor);
        try
        {
            FileReader fileReader = new FileReader(filename);
            return yaml.load(fileReader);
        }
        catch (FileNotFoundException e)
        {
            throw new CasualRuntimeException(e);
        }
    }
}
