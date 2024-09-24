/*
 * Copyright (c) 2021 - 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.Flag;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TpCaller
{
    ServiceReturn<CasualBuffer> tpcall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags, ConnectionFactoryLookup lookup);
    CompletableFuture<Optional<ServiceReturn<CasualBuffer>>> tpacall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags, ConnectionFactoryLookup lookup);
}
