/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.functions;

import jakarta.resource.ResourceException;

@FunctionalInterface
public interface BiFunctionThrowsResourceException<T, U, R>
{
    R apply(T arg1, U arg2) throws ResourceException;
}



