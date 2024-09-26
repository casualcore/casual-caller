/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.functions;

import jakarta.resource.ResourceException;
import se.laz.casual.jca.CasualConnection;

import java.util.UUID;

@FunctionalInterface
public interface FunctionThrowsResourceException <R>
{
    R apply(CasualConnection connection, UUID execution) throws ResourceException;
}
