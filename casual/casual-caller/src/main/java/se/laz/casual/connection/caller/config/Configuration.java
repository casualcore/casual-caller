/*
 * Copyright (c) 2022-2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.config;

import java.util.Objects;
import java.util.Optional;

public class Configuration
{
    public static final String CASUAL_CALLER_CONNECTION_FACTORY_JNDI_SEARCH_ROOT_ENV_NAME = "CASUAL_CALLER_CONNECTION_FACTORY_JNDI_SEARCH_ROOT";
    public static final String CASUAL_CALLER_VALIDATION_INTERVAL_ENV_NAME = "CASUAL_CALLER_VALIDATION_INTERVAL";
    public static final String CASUAL_CALLER_TRANSACTION_STICKY_ENV_NAME = "CASUAL_CALLER_TRANSACTION_STICKY";
    public static final String CASUAL_CALLER_TOPOLOGY_CHANGED_DELAY_ENV_NAME = "CASUAL_CALLER_TOPOLOGY_CHANGED_DELAY";
    public static final String ROUTE_FILE_ENV_NAME="CASUAL_CALLER_SERVICE_ROUTES_FILE";

    public static final String DEFAULT_JNDI_SEARCH_ROOT = "eis";
    public static final String DEFAULT_VALIDATION_INTERVAL_MILLIS = "5000";
    public static final String DEFAULT_TRANSACTION_STICKY = "false";
    public static final String DEFAULT_TOPOLOGY_CHANGED_DELAY = "50";

    private String jndiSearchRoot;
    private Integer validationIntervalMillis;
    private Boolean transactionStickyEnabled;
    private Long topologyChangeDelayMillis;
    private String routeFileName;

    private Configuration(Builder builder)
    {
        jndiSearchRoot = builder.jndiSearchRoot;
        validationIntervalMillis = builder.validationIntervalMillis;
        transactionStickyEnabled = builder.transactionStickyEnabled;
        topologyChangeDelayMillis = builder.topologyChangeDelayMillis;
        routeFileName = builder.routeFilename;
    }

    public String getJndiSearchRoot()
    {
        if (jndiSearchRoot == null) jndiSearchRoot = getJndiSearchRootFromEnv();
        return jndiSearchRoot;
    }

    public int getValidationIntervalMillis()
    {
        if (validationIntervalMillis == null) validationIntervalMillis = getValidationIntervalMillisFromEnv();
        return validationIntervalMillis;
    }

    public boolean isTransactionStickyEnabled()
    {
        if (transactionStickyEnabled == null) transactionStickyEnabled = isTransactionStickyEnabledFromEnv();
        return transactionStickyEnabled;
    }

    public long getTopologyChangeDelayMillis()
    {
        if(null == topologyChangeDelayMillis)
        {
            topologyChangeDelayMillis = getTopologyChangeDelayMillisFromEnv();
        }
        return topologyChangeDelayMillis;
    }

    public Optional<String> getRouteFileName()
    {
        if(null == routeFileName)
        {
            routeFileName = getRouteFilenameFromEnv();
        }
        return Optional.ofNullable(routeFileName);
    }

    public static Configuration fromEnvOrDefaults()
    {
        return builder()
                .withJndiSearchRoot(getJndiSearchRootFromEnv())
                .withValidationIntervalMillis(getValidationIntervalMillisFromEnv())
                .withTransactionStickyEnabled(isTransactionStickyEnabledFromEnv())
                .withTopologyChangeDelayMillis(getTopologyChangeDelayMillisFromEnv())
                .withRouteFilename(getRouteFilenameFromEnv())
                .build();
    }

    private static String getRouteFilenameFromEnv()
    {
        return System.getenv(ROUTE_FILE_ENV_NAME);
    }

    private static String getJndiSearchRootFromEnv()
    {
        return Optional.ofNullable(System.getenv(CASUAL_CALLER_CONNECTION_FACTORY_JNDI_SEARCH_ROOT_ENV_NAME))
                .orElse(DEFAULT_JNDI_SEARCH_ROOT);
    }

    private static int getValidationIntervalMillisFromEnv()
    {
        return Integer.parseInt(
                Optional.ofNullable(System.getenv(CASUAL_CALLER_VALIDATION_INTERVAL_ENV_NAME))
                        .orElse(DEFAULT_VALIDATION_INTERVAL_MILLIS));
    }

    private static boolean isTransactionStickyEnabledFromEnv()
    {
        return Boolean.parseBoolean(
                Optional.ofNullable(System.getenv(CASUAL_CALLER_TRANSACTION_STICKY_ENV_NAME))
                        .orElse(DEFAULT_TRANSACTION_STICKY));
    }

    private static Long getTopologyChangeDelayMillisFromEnv()
    {
        return Long.parseLong((
                Optional.ofNullable(System.getenv(CASUAL_CALLER_TOPOLOGY_CHANGED_DELAY_ENV_NAME))
                        .orElse(DEFAULT_TOPOLOGY_CHANGED_DELAY)));
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        Configuration that = (Configuration) o;
        return Objects.equals(getJndiSearchRoot(), that.getJndiSearchRoot()) &&
                Objects.equals(getValidationIntervalMillis(), that.getValidationIntervalMillis()) &&
                Objects.equals(isTransactionStickyEnabled(), that.isTransactionStickyEnabled()) &&
                Objects.equals(getTopologyChangeDelayMillis(), that.getTopologyChangeDelayMillis()) &&
                Objects.equals(getRouteFileName(), that.getRouteFileName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getJndiSearchRoot(), getValidationIntervalMillis(),
                isTransactionStickyEnabled(), getTopologyChangeDelayMillis(), getRouteFileName());
    }

    @Override
    public String toString()
    {
        return "Configuration{" +
                "jndiSearchRoot='" + getJndiSearchRoot() + '\'' +
                ", validationIntervalMillis=" + getValidationIntervalMillis() +
                ", transactionStickyEnabled=" + isTransactionStickyEnabled() +
                ", topologyChangeDelayMillis=" + getTopologyChangeDelayMillis() +
                ", routeFileName='" + getRouteFileName() +
                '}';
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private String jndiSearchRoot;
        private Integer validationIntervalMillis;
        private Boolean transactionStickyEnabled;
        private Long topologyChangeDelayMillis;
        private String routeFilename;

        public Configuration build()
        {
            return new Configuration(this);
        }

        public Builder withJndiSearchRoot(String jndiSearchRoot)
        {
            this.jndiSearchRoot = jndiSearchRoot;
            return this;
        }

        public Builder withValidationIntervalMillis(Integer validationIntervalMillis)
        {
            this.validationIntervalMillis = validationIntervalMillis;
            return this;
        }

        public Builder withTransactionStickyEnabled(Boolean transactionStickyEnabled)
        {
            this.transactionStickyEnabled = transactionStickyEnabled;
            return this;
        }

        public Builder withTopologyChangeDelayMillis(Long domainDiscoveryOnTopologyChangeDelayMillis)
        {
            this.topologyChangeDelayMillis = domainDiscoveryOnTopologyChangeDelayMillis;
            return this;
        }

        public Builder withRouteFilename(String routeFilename)
        {
            this.routeFilename = routeFilename;
            return this;
        }
    }
}
