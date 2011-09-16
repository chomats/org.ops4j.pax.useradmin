package org.ops4j.pax.useradmin.provider.ldap.internal;

import java.util.Collection;
import java.util.Dictionary;

import org.ops4j.pax.useradmin.service.spi.ExtendedRole;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;

/***
 * Create a proxy to call the bind method.
 * @author SESA170017
 *
 */
public class UserImplWithBind implements User, ExtendedRole {
    StorageProviderImpl m_st;
    User m_user;
    String m_key;
    
    protected UserImplWithBind(StorageProviderImpl st, User user) {
        m_user = user;
        this.m_st = st;
    }
    
    public boolean hasCredential(String key, Object value) {
        if (m_st.useBindMethod())
            return m_st.bind(m_key, getName(), (String) value);
        return m_user.hasCredential(key, value);
    }

    @SuppressWarnings("rawtypes")
    public Dictionary getCredentials() {
        return m_user.getCredentials();
    }

    public String getName() {
        return m_user.getName();
    }

    public int getType() {
        return m_user.getType();
    }

    @SuppressWarnings("rawtypes")
    public Dictionary getProperties() {
        return m_user.getProperties();
    }

    public void setKey(String key) {
        m_key = key;
    }

    public ImplicationResult isImpliedBy(Role role,
            Collection<String> checkedRoles) {
        return ((ExtendedRole) m_user).isImpliedBy(role, checkedRoles);
    }

}
