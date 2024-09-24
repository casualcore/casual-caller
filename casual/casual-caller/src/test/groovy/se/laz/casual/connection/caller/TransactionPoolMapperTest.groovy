/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import se.laz.casual.api.CasualRuntimeException
import spock.lang.Shared
import spock.lang.Specification

import jakarta.transaction.Status
import jakarta.transaction.Transaction

class TransactionPoolMapperTest extends Specification
{
    @Shared
    TransactionManagerImpl transactionManager

    def setup()
    {
        TransactionPoolMapper.resetForTest()
        TransactionPoolMapper.getInstance().setActiveForTest(true)
        TransactionPoolMapper.getInstance().setTransactionManager(transactionManager = new TransactionManagerImpl())
    }

    def cleanupSpec()
    {
        TransactionPoolMapper.resetForTest()
    }

    def "Test default config state, should be disabled/inactive"()
    {
        setup:
        TransactionPoolMapper.resetForTest()

        expect:
        !TransactionPoolMapper.getInstance().isPoolMappingActive()
    }

    def "Test with single transaction"()
    {
        setup:
        Transaction transaction = new TransactionImpl(Status.STATUS_ACTIVE)
        transactionManager.setCurrentTransaction(transaction)

        String actualPoolName = "hello, world!"
        UUID execution = UUID.randomUUID()
        StickyInformation stickyInformation = new StickyInformation(actualPoolName, execution)
        TransactionPoolMapper.getInstance().setStickyInformationForCurrentTransaction(stickyInformation)

        expect:
        TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().poolName() == actualPoolName
        TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().execution() == execution
        TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
    }

    def "Test with many different transaction instances"()
    {
        setup:
        int transactions = 5

        for (int i = 0; i < transactions; i++)
        {
            transactionManager.setCurrentTransaction(new TransactionImpl(Status.STATUS_ACTIVE))
            StickyInformation stickyInformation = new StickyInformation("hello transaction " + i, UUID.randomUUID())
            TransactionPoolMapper.getInstance().setStickyInformationForCurrentTransaction(stickyInformation)
        }

        expect:
        TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == transactions
    }

    def "Should throw exception if attempting to set sticky for an already stickied transaction"()
    {
        given:
        transactionManager.setCurrentTransaction(new TransactionImpl(Status.STATUS_ACTIVE))

        StickyInformation stickyInformation = new StickyInformation("eis/myPool", UUID.randomUUID())
        TransactionPoolMapper.getInstance().setStickyInformationForCurrentTransaction(stickyInformation)

        when:
        TransactionPoolMapper.getInstance().setStickyInformationForCurrentTransaction(new StickyInformation("eis/whateverPool", UUID.randomUUID()))

        then:
        def e = thrown(CasualRuntimeException)
        System.err.println(e.message)
        e.message.contains("already stickied")
    }
}
