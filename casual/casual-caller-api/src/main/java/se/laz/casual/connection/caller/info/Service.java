package se.laz.casual.connection.caller.info;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class Service implements Serializable
{
    @Serial
    private static final long serialVersionUID = 7441523856061920556L;
    private final String name;
    private final long hops;
    private final boolean valid;
    private final String jndiName;

    private Service(Builder builder)
    {
        this.name = builder.name;
        this.hops = builder.hops;
        this.valid = builder.valid;
        this.jndiName = builder.jndiName;

        Objects.requireNonNull( name );
        Objects.requireNonNull( jndiName );
    }

    public String getName() {
        return name;
    }

    public long getHops() {
        return hops;
    }

    public boolean isValid() {
        return valid;
    }

    public String getJndiName() {
        return jndiName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return hops == service.hops && valid == service.valid && Objects.equals(name, service.name) && Objects.equals(jndiName, service.jndiName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, hops, valid, jndiName);
    }

    @Override
    public String toString() {
        return "Service{" +
                "name='" + name + '\'' +
                ", hops=" + hops +
                ", valid=" + valid +
                ", jndiName='" + jndiName + '\'' +
                '}';
    }

    public static class Builder {
        private String name;
        private long hops = 0; // 0 == inbound service
        private boolean valid = false;
        private String jndiName = "";

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder hops(long hops) {
            this.hops = hops;
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder jndiName(String jndiName) {
            this.jndiName = jndiName;
            return this;
        }

        public Service build() {
            return new Service(this);
        }
    }
}
