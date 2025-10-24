/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.administration.model;

import se.laz.casual.network.messages.domain.TransactionType;

public record Service(String name, String category, TransactionType transactionType, long timeout, long hops,
                      ServiceConnection serviceConnection) {
}
