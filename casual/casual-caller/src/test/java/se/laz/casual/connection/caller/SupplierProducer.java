package se.laz.casual.connection.caller;

import java.util.function.Supplier;

public class SupplierProducer
{
    public static <T> Supplier<T> get(final T data)
    {
        return () -> data;
    }

}
