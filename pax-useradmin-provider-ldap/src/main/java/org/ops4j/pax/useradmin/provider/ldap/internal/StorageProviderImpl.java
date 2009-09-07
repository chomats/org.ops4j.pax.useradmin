/*
 * Copyright 2009 Matthias Kuespert
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.useradmin.provider.ldap.internal;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.ops4j.pax.useradmin.provider.ldap.ConfigurationConstants;
import org.ops4j.pax.useradmin.service.spi.StorageProvider;
import org.ops4j.pax.useradmin.service.spi.StorageException;
import org.ops4j.pax.useradmin.service.spi.UserAdminFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;

import com.novell.ldap.LDAPAttribute;
import com.novell.ldap.LDAPAttributeSet;
import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPEntry;
import com.novell.ldap.LDAPException;
import com.novell.ldap.LDAPModification;
import com.novell.ldap.LDAPSearchResults;

/**
 * A LDAP based implementation of the StorageProvider SPI.
 * 
 * @author Matthias Kuespert
 * @since  02.07.2009
 */
public class StorageProviderImpl implements StorageProvider, ManagedService {

    private static final String BASIC_EXT                  = ".basic";
    private static final String REQUIRED_EXT               = ".required";

    private static final String PATTERN_YES_TRUE           = "(yes|true)";

    private static final String PATTERN_SPLIT_LIST_VALUE   = ", *";

    // configuration
    
    private String              m_accessUser               = "";
    private String              m_accessPassword           = "";
    private String              m_host                     = ConfigurationConstants.DEFAULT_LDAP_SERVER_URL;
    private String              m_port                     = ConfigurationConstants.DEFAULT_LDAP_SERVER_PORT;

    private String              m_rootDN                   = ConfigurationConstants.DEFAULT_LDAP_ROOT_DN;
    private String              m_rootUsersDN              = ConfigurationConstants.DEFAULT_LDAP_ROOT_USERS
                                                                           + "," + m_rootDN;
    private String              m_rootGroupsDN             = ConfigurationConstants.DEFAULT_LDAP_ROOT_GROUPS
                                                                                    + "," + m_rootDN;

    private String              m_userObjectclass          = ConfigurationConstants.DEFAULT_USER_OBJECTCLASS;
    private String              m_userIdAttr               = ConfigurationConstants.DEFAULT_USER_ATTR_ID;
    private String              m_userMandatoryAttr        = ConfigurationConstants.DEFAULT_USER_ATTR_MANDATORY;
    private String              m_userCredentialAttr       = ConfigurationConstants.DEFAULT_USER_ATTR_CREDENTIAL;

    private String              m_groupObjectclass         = ConfigurationConstants.DEFAULT_GROUP_OBJECTCLASS;
    private String              m_groupIdAttr              = ConfigurationConstants.DEFAULT_GROUP_ATTR_ID;

    private String              m_groupEntryObjectclass    = ConfigurationConstants.DEFAULT_GROUP_ENTRY_OBJECTCLASS;
    private String              m_groupEntryIdAttr         = ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_ID;
    private String              m_groupEntryMandatoryAttr  = ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_MANDATORY;
    private String              m_groupEntryMemberAttr     = ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_MEMBER;
    private String              m_groupEntryCredentialAttr = ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_CREDENTIAL;

    private boolean             m_doAllowEmptyGroups       = ConfigurationConstants.DEFAULT_GROUP_ALLOW_EMPTY
                                                                                   .matches(PATTERN_YES_TRUE);
    private String              m_defaultGroupMember       = ConfigurationConstants.DEFAULT_GROUP_DEFAULT_MEMBER;

    /**
     * The connection which is used for access.
     */
    private LDAPConnection m_connection = null;

    /**
     * Constructor.
     * 
     * @param connection Unit tests can provide a mock via this parameter.
     */
    protected StorageProviderImpl(BundleContext context, LDAPConnection connection) {
        if (null == connection) {
            throw new IllegalArgumentException("Internal error: no LDAPConnection object specified when constructing the StorageProvider instance.");
        }
        m_connection = connection;
        if (null == context) {
            throw new IllegalArgumentException("Internal error: no BundleContext object specified when constructing the StorageProvider instance.");
        }
    }

    // ManagedService interface
    
    /**
     * Retrieves a property and throws an exception if not found.
     * 
     * @param properties The properties used for lookup.
     * @param name The name of the property to retrieve.
     * @return The value of the property.
     * @throws ConfigurationException If the property is not found in the given properties.
     * @throws IllegalArgumentException If the properties argument is null.
     */
    private static String getMandatoryProperty(Dictionary<String, String> properties,
                                               String name) throws ConfigurationException {
        if (null == properties) {
            throw new IllegalArgumentException("getMandatoryProperty() argument 'properties' must not be null");
        }
        String value = properties.get(name);
        if (null == value) {
            throw new ConfigurationException(name,
                                               "no value found for property '"
                                             + name
                                             + "' - please check the configuration");
        }
        return value;
    }

    /**
     * Retrieves a property and returns a default value if not found.
     * 
     * @param properties The properties used for lookup.
     * @param name The name of the property to retrieve.
     * @param defaultValue The default value to return if the property is not found.
     * @return The value of the property or the given default value.
     * @throws IllegalArgumentException If the properties argument is null.
     */
    private static String getOptionalProperty(Dictionary<String, String> properties,
                                              String name,
                                              String defaultValue) {
        if (null == properties) {
            throw new IllegalArgumentException("getMandatoryProperty() argument 'properties' must not be null");
        }
        String value = properties.get(name);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * @see ManagedService#updated(Dictionary)
     */
    @SuppressWarnings(value = "unchecked")
    public void updated(Dictionary properties) throws ConfigurationException {
        if (null == properties) {
            // ignore empty properties
            return;
        }
        //
        m_accessUser = getOptionalProperty(properties,
                                           ConfigurationConstants.PROP_LDAP_ACCESS_USER, "");
        m_accessPassword = getOptionalProperty(properties,
                                               ConfigurationConstants.PROP_LDAP_ACCESS_PWD, "");
        //
        m_host = getOptionalProperty(properties, ConfigurationConstants.PROP_LDAP_SERVER_URL,
                                     ConfigurationConstants.DEFAULT_LDAP_SERVER_URL);
        m_port = getOptionalProperty(properties, ConfigurationConstants.PROP_LDAP_SERVER_PORT,
                                     ConfigurationConstants.DEFAULT_LDAP_SERVER_PORT);
        //
        m_rootDN = getMandatoryProperty(properties, ConfigurationConstants.PROP_LDAP_ROOT_DN);
        m_rootUsersDN = getOptionalProperty(properties,
                                            ConfigurationConstants.PROP_LDAP_ROOT_USERS,
                                            ConfigurationConstants.DEFAULT_LDAP_ROOT_USERS)
                        + "," + m_rootDN;
        m_rootGroupsDN = getOptionalProperty(properties,
                                             ConfigurationConstants.PROP_LDAP_ROOT_GROUPS,
                                             ConfigurationConstants.DEFAULT_LDAP_ROOT_GROUPS)
                         + "," + m_rootDN;

        m_userObjectclass = getOptionalProperty(properties,
                                                ConfigurationConstants.PROP_USER_OBJECTCLASS,
                                                ConfigurationConstants.DEFAULT_USER_OBJECTCLASS);
        m_userIdAttr = getOptionalProperty(properties,
                                           ConfigurationConstants.PROP_USER_ATTR_ID,
                                           ConfigurationConstants.DEFAULT_USER_ATTR_ID);
        m_userMandatoryAttr = getOptionalProperty(properties,
                                                  ConfigurationConstants.PROP_USER_ATTR_MANDATORY,
                                                  ConfigurationConstants.DEFAULT_USER_ATTR_MANDATORY);

        m_groupObjectclass = getOptionalProperty(properties,
                                                 ConfigurationConstants.PROP_GROUP_OBJECTCLASS,
                                                 ConfigurationConstants.DEFAULT_GROUP_OBJECTCLASS);
        m_groupIdAttr = getOptionalProperty(properties,
                                            ConfigurationConstants.PROP_GROUP_ATTR_ID,
                                            ConfigurationConstants.DEFAULT_GROUP_ATTR_ID);

        m_groupEntryObjectclass = getOptionalProperty(properties,
                                                      ConfigurationConstants.PROP_GROUP_ENTRY_OBJECTCLASS,
                                                      ConfigurationConstants.DEFAULT_GROUP_ENTRY_OBJECTCLASS);
        m_groupEntryIdAttr = getOptionalProperty(properties,
                                                 ConfigurationConstants.PROP_GROUP_ENTRY_ATTR_ID,
                                                 ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_ID);
        m_groupEntryMandatoryAttr = getOptionalProperty(properties,
                                                        ConfigurationConstants.PROP_GROUP_ENTRY_ATTR_MANDATORY,
                                                        ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_MANDATORY);
        m_groupEntryMemberAttr = getOptionalProperty(properties,
                                                     ConfigurationConstants.PROP_GROUP_ENTRY_ATTR_MEMBER,
                                                     ConfigurationConstants.DEFAULT_GROUP_ENTRY_ATTR_MEMBER);
        // todo: obsolete?
        m_doAllowEmptyGroups = getOptionalProperty(properties, ConfigurationConstants.PROP_GROUP_ALLOW_EMPTY,
                                                   ConfigurationConstants.DEFAULT_GROUP_ALLOW_EMPTY).matches(PATTERN_YES_TRUE);
        m_defaultGroupMember = getOptionalProperty(properties, ConfigurationConstants.PROP_GROUP_DEFAULT_MEMBER,
                                                   ConfigurationConstants.DEFAULT_GROUP_DEFAULT_MEMBER);
    }

    // StorageProvider interface
    
    /**
     * Opens a connection to the LDAP server.
     * 
     * Each public method implementation of this <code>StorageProvider</code>
     * must open and close a connection to the LDAP server.
     *
     * @see StorageProviderImpl#closeConnection()
     * 
     * @return An initialized connection.
     * @throws StorageException If the connection could not be initialized.
     */
    private LDAPConnection openConnection() throws StorageException {
        try {
            if (m_connection.isConnected() || m_connection.isBound()) {
                m_connection.disconnect();
            }
            m_connection.connect(m_host, new Integer(m_port));
            m_connection.bind(LDAPConnection.LDAP_V3, m_accessUser, m_accessPassword.getBytes("UTF8"));
            return m_connection;
        } catch (LDAPException e) {
            throw new StorageException("Error opening connection to LDAP server '"
                                       + m_host + ":" + m_port + "': " + e.getMessage() + " - " + e.getLDAPErrorMessage());
        } catch (UnsupportedEncodingException e) {
            throw new StorageException("Unknown encoding when opening connection: " + e.getMessage());
        }
    }

    /**
     * Closes the current connection.
     * 
     * Each public method implementation of this <code>StorageProvider</code>
     * must open and close a connection to the LDAP server.
     *
     * @see StorageProviderImpl#openConnection()
     * 
     * @throws StorageException
     */
    private void closeConnection() throws StorageException {
        try {
            m_connection.disconnect();
        } catch (LDAPException e) {
            throw new StorageException("Error closing connection: " + e.getMessage());
        }
    }

    private String getUserDN(String userName) {
        return m_userIdAttr + "=" + userName + "," + m_rootUsersDN;
    }
    
    private String getGroupDN(String groupName) {
        return m_groupIdAttr + "=" + groupName + "," + m_rootGroupsDN;
    }

    private String getBasicGroupDN(String groupName) {
        return m_groupEntryIdAttr + "=" + groupName + BASIC_EXT + ",ou=" + groupName + "," + m_rootGroupsDN;
    }

    private String getRequiredGroupDN(String groupName) {
        return m_groupEntryIdAttr + "=" + groupName + REQUIRED_EXT + ",ou=" + groupName + "," + m_rootGroupsDN;
    }
    
    private String getRoleDN(Role role) throws StorageException {
        String dn;
        switch (role.getType()) {
            case Role.USER:
                dn = getUserDN(role.getName());
                break;
            case Role.GROUP:
                dn = getGroupDN(role.getName());
                // dn = m_groupEntryIdAttr + "=" + role.getName() + "," + m_rootGroupsDN;
                break;
            default:
                throw new StorageException("Invalid role type '" + role.getType() + "'");
        }
        return dn;
    }
    
    /**
     * Returns true if all the configured objectclasses are included in the given objectclass list.
     * 
     * @param configuredClasslist The classes to check for.
     * @param objectClasses The list to check.
     * @return True if all classes are found in the list.
     */
    private boolean configuredClassedContainedInObjectClasses(String configuredClasslist, String[] objectClasses) {
        for (String typeName : configuredClasslist.split(PATTERN_SPLIT_LIST_VALUE)) {
            boolean contained = false;
            for (String objectClass : objectClasses) {
                if (objectClass.trim().equals(typeName.trim())) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Calculates a role type from the list of object classes specified for the given LDAP entry.
     * 
     * @param entry The LDAP entry to check.
     * @return A role type as specified by the Role interface.
     * @throws StorageException if the role type could not be determined.
     */
    private int getRoleType(LDAPEntry entry) throws StorageException {
        LDAPAttribute typeAttr = entry.getAttribute(ConfigurationConstants.ATTR_OBJECTCLASS);
        if (null == typeAttr) {
            throw new StorageException("No type attribute '" + ConfigurationConstants.ATTR_OBJECTCLASS + "' found for entry: " + entry);
        }
        String[] objectClasses = typeAttr.getStringValueArray();
        //
        // check if all our required objectclasses are contained in the
        // given list
        if (configuredClassedContainedInObjectClasses(m_userObjectclass, objectClasses)) {
            return Role.USER;
        }
        if (configuredClassedContainedInObjectClasses(m_groupObjectclass, objectClasses)) {
            return Role.GROUP;
        }
        // error handling ...
        String classes = "";
        for (String clazz : objectClasses) {
            if (classes.length() > 0) {
                classes += ", ";
            }
            classes += clazz;
        }
        throw new StorageException("Could not determine role type for objectClasses: '" + classes + "'.");
        // return Role.ROLE;
    }

    /**
     * Creates a Role for the given LDAP entry.
     * 
     * @param factory The factory to use for creation.
     * @param entry The entry to create a role for.
     * @return The created role or null.
     * @throws StorageException if the entry does not map to a role.
     */
    @SuppressWarnings(value = "unchecked")
    private Role createRole(UserAdminFactory factory, LDAPEntry entry) throws StorageException {
        Map<String, Object> properties = new HashMap<String, Object>();
        Map<String, Object> credentials = new HashMap<String, Object>();
        // first determine the type from the objectclasses
        int type = getRoleType(entry);
        // then read additional attributes
        Iterator<LDAPAttribute> it = entry.getAttributeSet().iterator();
        while (it.hasNext()) {
            LDAPAttribute attribute = it.next();
            if (ConfigurationConstants.ATTR_OBJECTCLASS.equals(attribute.getName())) {
                // ignore: we've read that already
            } else if (   (type == Role.GROUP && m_groupEntryCredentialAttr.equals(attribute.getName()))
                       || (type == Role.USER && m_userCredentialAttr.equals(attribute.getName()))) {
                for (String value : attribute.getStringValueArray()) {
                    String[] data = value.split("; *");
                    if (2 != data.length) {
                        throw new StorageException("Wrong credential format '" + value + "' found for entry: " + entry);
                    }
                    credentials.put(data[0], data[1]);
                }
            } else {
                properties.put(attribute.getName(), attribute.getStringValue());
            }
        }
        switch (type) {
            case Role.USER:
                return factory.createUser(entry.getAttribute(m_userIdAttr).getStringValue(), properties, credentials);
            case Role.GROUP:
                return factory.createGroup(entry.getAttribute(m_groupIdAttr).getStringValue(), properties, credentials);
            default:
                // should never happen: getRoleType() throws on this
                throw new StorageException("Unexpected role type '" + type + "' (0==Role) detected.");
        }
    }
    
    /**
     * Returns the entry with the given DN if it exists, null otherwise.
     *  
     * @param connection
     * @param dn
     * @return
     * @throws LDAPException
     */
    private LDAPEntry getEntry(LDAPConnection connection, String dn) throws LDAPException {
        LDAPEntry entry = null;
        try {
            entry = connection.read(dn);
        } catch (LDAPException e) {
            if (e.getResultCode() != LDAPException.NO_SUCH_OBJECT) {
                // rethrow other errors
                throw e;
            } // ignore and return null
        }
        return entry;
    }
    
    /**
     * Retrieves an LDAP entry based on the given name.
     * 
     * @param connection The LDAP connection to use.
     * @param name The name of the entry to search for.
     * @return The LDAP entry that matches the given name.
     * @throws LDAPException if an LDAP error occurs.
     */
    private LDAPEntry getEntryForName(LDAPConnection connection, String name) throws LDAPException {
        LDAPEntry entry = getEntry(connection, getGroupDN(name));
        if (null == entry) {
            entry = getEntry(connection, getUserDN(name));
        }
        return entry;
    }
    
    /**
     * Creates an entry for a group. Group entries are stored in two sub-entries below the group node: the 'basic' or 'required' group entries.
     * 
     * @param connection The LDAP connection to use.
     * @param isBasic True if the initialMember should be added to the 'basic' members - on false it is added to the 'required' members.
     * @param group The group to modify.
     * @param initialMember The initial member to add to this group.
     * @return The LDAP entry that was created for this group entry.
     * @throws LDAPException if an LDAP error occurs.
     */
    private LDAPEntry createGroupEntry(LDAPConnection connection, boolean isBasic, Group group, Role initialMember) throws LDAPException {
        String entryName = group.getName() + (isBasic ? BASIC_EXT : REQUIRED_EXT);
        //
        // set objectclass attributes
        //
        LDAPAttributeSet attributes = new LDAPAttributeSet();
        attributes.add(new LDAPAttribute(ConfigurationConstants.ATTR_OBJECTCLASS,
                                         m_groupEntryObjectclass.split(PATTERN_SPLIT_LIST_VALUE)));
        // set ID attribute
        //
        attributes.add(new LDAPAttribute(m_groupEntryIdAttr, entryName));
        //
        // add initial user
        //
        String initialUserDN = getUserDN(initialMember.getName());
        attributes.add(new LDAPAttribute(m_groupEntryMemberAttr, initialUserDN));
        //
        // set all mandatory attributes to name
        //
        if (!"".equals(m_groupEntryMandatoryAttr)) {
            for (String attr : m_groupEntryMandatoryAttr.split(PATTERN_SPLIT_LIST_VALUE)) {
                attributes.add(new LDAPAttribute(attr.trim(), entryName));
            }
        }
        // create and add entry
        LDAPEntry entry = new LDAPEntry(m_groupEntryIdAttr + "=" + entryName + "," + getGroupDN(group.getName()), attributes);
        connection.add(entry);
        return entry;
    }

    /**
     * Removes the given DN from the group members.
     * 
     * @param connection The LDAP connection to use.
     * @param groupEntry The group entry to modify. 
     * @param memberDN The DN of the member to remove.
     * @return True if the member was sucessfully removed - false otherwise.
     * @throws LDAPException if an LDAP error occurs.
     */
    private boolean removeGroupMember(LDAPConnection connection, LDAPEntry groupEntry, String memberDN) throws LDAPException {
        if (null != groupEntry) {
            Iterator<LDAPAttribute> it = groupEntry.getAttributeSet().iterator();
            while (it.hasNext()) {
                LDAPAttribute attribute = it.next();
                if (m_groupEntryMemberAttr.equals(attribute.getName())) {
                    for (String userDN : attribute.getStringValueArray()) {
                        if (userDN.equals(memberDN)) {
                            // found: let's remove this member from the group ...
                            LDAPModification modification = new LDAPModification(LDAPModification.DELETE, new LDAPAttribute(m_groupEntryMemberAttr, memberDN));
                            connection.modify(groupEntry.getDN(), modification);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * @see StorageProvider#createUser(UserAdminFactory, String)
     */
    public User createUser(UserAdminFactory factory, String name) throws StorageException {
        LDAPConnection connection = openConnection();
        // fill attribute set (for LDAP creation) and properties (for UserAdmin creation)
        LDAPAttributeSet attributes = new LDAPAttributeSet();
        Map<String, Object> properties = new HashMap<String, Object>();
        //
        attributes.add(new LDAPAttribute(ConfigurationConstants.ATTR_OBJECTCLASS,
                                         m_userObjectclass.split(PATTERN_SPLIT_LIST_VALUE)));
        attributes.add(new LDAPAttribute(m_userIdAttr, name));
        properties.put(m_userIdAttr, name);
        // set all mandatory attributes to name
        if (!"".equals(m_userMandatoryAttr)) {
            for (String attr : m_userMandatoryAttr.split(PATTERN_SPLIT_LIST_VALUE)) {
                attributes.add(new LDAPAttribute(attr.trim(), name));
                properties.put(attr.trim(), name);
            }
        }
        //
        LDAPEntry entry = new LDAPEntry(getUserDN(name), attributes);
        //
        try {
            connection.add(entry);
            return factory.createUser(name, properties, null);
        } catch (LDAPException e) {
            throw new StorageException(  "Error creating user '" + name + "' " + entry + ": "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public Group createGroup(UserAdminFactory factory, String name) throws StorageException {
        // create ou as container for basic and required group objects
        //
        LDAPAttributeSet attributes = new LDAPAttributeSet();
        attributes.add(new LDAPAttribute(ConfigurationConstants.ATTR_OBJECTCLASS,
                                         m_groupObjectclass.split(PATTERN_SPLIT_LIST_VALUE))); // new String[] { "organizationalUnit", "top" }));
        attributes.add(new LDAPAttribute(m_groupIdAttr, name));
        //
        LDAPEntry entry = new LDAPEntry(getGroupDN(name), attributes);
        //
        LDAPConnection connection = openConnection();
        try {
            connection.add(entry);
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put(m_groupIdAttr, name);
            return factory.createGroup(name, properties, null);
        } catch (LDAPException e) {
            throw new StorageException(  "Error creating group '" + name + "' " + entry + ": "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public boolean deleteRole(Role role) throws StorageException {
        String dn = getRoleDN(role);
        LDAPConnection connection = openConnection();
        // todo: check for group memberships??
        try {
            connection.delete(dn);
            return true;
        } catch (LDAPException e) {
            throw new StorageException(  "Error deleting role with name '" + role.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    @SuppressWarnings(value = "unchecked")
    public Collection<Role> getMembers(UserAdminFactory factory, Group group) throws StorageException {
        // check if a <name>.basic group exists
        // if yes return members
        Collection<Role> roles = new ArrayList<Role>();
        LDAPConnection connection = openConnection();
        try {
            // get the group ou-entry
            //
            LDAPEntry groupEntry = getEntry(connection, getGroupDN(group.getName()));
            if (null == groupEntry) {
                throw new StorageException(  "Internal error: entry for group '" + group.getName()
                                             + "' could not be retrieved.");
            }
            // if there is a <name>.basic group return its members
            LDAPEntry basicGroupEntry = getEntry(connection, getBasicGroupDN(group.getName()));
            if (null != basicGroupEntry) {
                Iterator<LDAPAttribute> it = basicGroupEntry.getAttributeSet().iterator();
                while (it.hasNext()) {
                    LDAPAttribute attribute = it.next();
                    if (m_groupEntryMemberAttr.equals(attribute.getName())) {
                        for (String userDN : attribute.getStringValueArray()) {
                            // TODO: should we have a ldap.ignoreDefaultMemberInAPI option?
//                            if (userDN.startsWith(USER_NOBODY)) {
//                                // ignore dummy user
//                                continue;
//                            }
                            LDAPEntry userEntry = getEntry(connection,userDN);
                            if (null == userEntry) {
                                throw new StorageException(  "Internal error: group member '" + userDN
                                                             + "' could not be retrieved.");
                            }
                            Role role = createRole(factory, userEntry);
                            roles.add(role);
                        }
                    }
                }
            }
        } catch (LDAPException e) {
            throw new StorageException(  "Error retrieving role with name '" + group.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
        return roles;
    }
    
    public Collection<Role> getRequiredMembers(UserAdminFactory factory, Group group) throws StorageException {
        // TODO: add required member handling
        throw new IllegalStateException("required member handling is not yet implemented");
    }
    
    @SuppressWarnings(value = "unchecked")
    public boolean addMember(Group group, Role role) throws StorageException {
        // check if a <name>.basic group exists
        // if no create it
        
        LDAPConnection connection = openConnection();
        try {
            // get the group main entry
            //
            LDAPEntry groupEntry = getEntry(connection, getGroupDN(group.getName()));
            if (null == groupEntry) {
                throw new StorageException(  "Internal error: entry for group '" + group.getName()
                                           + "' could not be retrieved.");
            }
            // if there is no <name>.basic group create it
            LDAPEntry basicGroupEntry = getEntry(connection, getBasicGroupDN(group.getName()));
            if (null == basicGroupEntry) {
 
                basicGroupEntry = createGroupEntry(connection, true, group, role);
            } else {
                // add role to group entry
                String roleDN = getRoleDN(role);
                Iterator<LDAPAttribute> it = basicGroupEntry.getAttributeSet().iterator();
                while (it.hasNext()) {
                    LDAPAttribute attribute = it.next();
                    if (m_groupEntryMemberAttr.equals(attribute.getName())) {
                        // check the member values
                        for (String memberDN : attribute.getStringValueArray()) {
                            if (roleDN.equals(memberDN)) {
                                // ignore already existing members
                                return false;
                            }
                        }
                        // add new member
                        attribute.addValue(roleDN);
                        LDAPModification modification = new LDAPModification(LDAPModification.REPLACE,
                                                                             new LDAPAttribute(attribute)); // new LDAPAttribute(m_groupEntryMemberAttr, roleDN));
                        connection.modify(basicGroupEntry.getDN(), modification);
                        break;
                    }
                }
            }
        } catch (LDAPException e) {
            throw new StorageException(  "Error adding member role with name '" +role.getName()
                                       + "' to group '" + group.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
        return true;
    }
    
    public boolean addRequiredMember(Group group, Role role) throws StorageException {
        // TODO: add required member handling
        throw new IllegalStateException("required member handling is not yet implemented");
    }
    
    public boolean removeMember(Group group, Role role) throws StorageException {
        LDAPConnection connection = openConnection();
        try {
            // get the group ou-entry
            //
            LDAPEntry groupEntry = getEntry(connection, getGroupDN(group.getName()));
            if (null == groupEntry) {
                throw new StorageException(  "Internal error: entry for group '" + group.getName()
                                             + "' could not be retrieved.");
            }
            LDAPEntry basicGroupEntry = getEntry(connection, getBasicGroupDN(group.getName()));
            LDAPEntry requiredGroupEntry = getEntry(connection, getRequiredGroupDN(group.getName()));
            String memberDN = getRoleDN(role);
            //
            return removeGroupMember(connection, basicGroupEntry, memberDN) || removeGroupMember(connection, requiredGroupEntry, memberDN);
            
        } catch (LDAPException e) {
            throw new StorageException(  "Error deleting role with name '" + group.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public void setRoleAttribute(Role role, String key, String value) throws StorageException {
        if (ConfigurationConstants.ATTR_OBJECTCLASS.equals(key)) {
            throw new StorageException(  "Cannot modify attribute '" + ConfigurationConstants.ATTR_OBJECTCLASS
                                       + "' - change the configuration instead.");
        }
        if (Role.USER == role.getType() && m_userIdAttr.equals(key)) {
            throw new StorageException(  "Cannot modify ID attribute '" + m_userIdAttr
                                       + "' - recreate the user instead.");
        }
        if (Role.GROUP == role.getType() && m_groupEntryIdAttr.equals(key)) {
            throw new StorageException(  "Cannot modify ID attribute '" + m_groupEntryIdAttr
                                       + "' - recreate the group instead.");
        }
        LDAPConnection connection = openConnection();
        try {
            String dn = getRoleDN(role);
            LDAPModification modification = new LDAPModification(LDAPModification.REPLACE, new LDAPAttribute(key, value));
            connection.modify(dn, modification);
        } catch (LDAPException e) {
            throw new StorageException(  "Error setting attribute '" + key + "' = '" + value
                                       + "' for role '" + role.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }

    public void setRoleAttribute(Role role, String key, byte[] value) throws StorageException {
        if (ConfigurationConstants.ATTR_OBJECTCLASS.equals(key)) {
            throw new StorageException(  "Cannot modify attribute '" + ConfigurationConstants.ATTR_OBJECTCLASS
                                         + "' - change the configuration instead.");
        }
        if (Role.USER == role.getType() && m_userIdAttr.equals(key)) {
            throw new StorageException(  "Cannot modify ID attribute '" + m_userIdAttr
                                       + "' - recreate the user instead.");
        }
        if (Role.GROUP == role.getType() && m_groupEntryIdAttr.equals(key)) {
            throw new StorageException(  "Cannot modify ID attribute '" + m_groupEntryIdAttr
                                       + "' - recreate the group instead.");
        }
        LDAPConnection connection = openConnection();
        try {
            String dn = getRoleDN(role);
            LDAPModification modification = new LDAPModification(LDAPModification.REPLACE, new LDAPAttribute(key, value));
            connection.modify(dn, modification);
        } catch (LDAPException e) {
            throw new StorageException(  "Error setting attribute '" + key + "' = '" + value
                                       + "' for role '" + role.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public void removeRoleAttribute(Role role, String key) throws StorageException {
        if (ConfigurationConstants.ATTR_OBJECTCLASS.equals(key)) {
            throw new StorageException(  "Cannot remove '" + ConfigurationConstants.ATTR_OBJECTCLASS
                                       + "' attribute - change the configuration instead.");
        }
        if (Role.USER == role.getType()) {
            if (m_userIdAttr.equals(key)) {
                throw new StorageException(  "Cannot remove mandatory ID attribute '" + m_userIdAttr
                                             + "'.");
            } else if (m_userMandatoryAttr.contains(key)) {
                throw new StorageException(  "Cannot remove mandatory attribute '" + key
                                             + "'.");
            }
        }
        if (Role.GROUP == role.getType()) {
            if (m_groupEntryIdAttr.equals(key)) {
                throw new StorageException(  "Cannot remove mandatory ID attribute '" + m_groupEntryIdAttr
                                             + "'.");
            } else if (m_userMandatoryAttr.contains(key)) {
                throw new StorageException(  "Cannot remove mandatory attribute '" + key
                                             + "'.");
            }
        }
        LDAPConnection connection = openConnection();
        try {
            String dn = getRoleDN(role);
            LDAPModification modification = new LDAPModification(LDAPModification.DELETE, new LDAPAttribute(key, ""));
            connection.modify(dn, modification);
        } catch (LDAPException e) {
            throw new StorageException(  "Error deleting attribute '" + key
                                       + "'of role '" + role.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    // TODO: how to detect dynamically which non-mandatory arguments to delete?
    public void clearRoleAttributes(Role role) throws StorageException {
        throw new IllegalStateException("clearing attributes is not yet implemented");
    }
    
    public void setUserCredential(User user, String key, String value) throws StorageException {
        LDAPConnection connection = openConnection();
        try {
//            LDAPEntry entry = readEntry(connection, user.getName());
            LDAPEntry entry = getEntryForName(connection, user.getName());
            if (null == entry) {
                throw new StorageException("Could not find user '" + user.getName() + "'");
            }
            String dn = getRoleDN(user);
            String attrName = (Role.USER == user.getType()) ? m_userCredentialAttr : m_groupEntryCredentialAttr; 
            LDAPAttribute attribute = entry.getAttribute(attrName);
            if (null != attribute) {
                for (String attrValue : attribute.getStringValueArray()) {
                    String[] data = attrValue.split("; *");
                    if (2 != data.length) {
                        throw new StorageException("Wrong credential format '" + value + "' found for entry: " + entry);
                    }
                    if (data[0].equals(key)) {
                        // modify existing entry
                        attribute.removeValue(attrValue);
                        attribute.addValue(key + ";" + value);
                        LDAPModification modification = new LDAPModification(LDAPModification.REPLACE, attribute);
                        connection.modify(dn, modification);
                        return;
                    }
                }
            } else {
                LDAPModification modification = new LDAPModification(LDAPModification.ADD,
                                                                     new LDAPAttribute(attrName, key + ";" + value));
                connection.modify(dn, modification);
                return;
            }
        } catch (LDAPException e) {
            throw new StorageException(  "Error setting credential for user '" + user.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }

    public void setUserCredential(User user, String key, byte[] value) throws StorageException {
        LDAPConnection connection = openConnection();
        try {
            LDAPEntry entry = getEntry(connection, getRoleDN(user)); // readEntry(connection, user.getName());
            if (null == entry) {
                throw new StorageException("Could not find user '" + user.getName() + "'");
            }
            String dn = getRoleDN(user);
            String attrName = (Role.USER == user.getType()) ? m_userCredentialAttr : m_groupEntryCredentialAttr; 
            LDAPAttribute attribute = entry.getAttribute(attrName);
            if (null != attribute) {
                for (String attrValue : attribute.getStringValueArray()) {
                    String[] data = attrValue.split("; *");
                    if (2 != data.length) {
                        throw new StorageException("Wrong credential format '" + value + "' found for entry: " + entry);
                    }
                    if (data[0].equals(key)) {
                        // modify existing entry
                        attribute.removeValue(attrValue);
                        attribute.addValue(key + ";" + value);
                        LDAPModification modification = new LDAPModification(LDAPModification.REPLACE, attribute);
                        connection.modify(dn, modification);
                        return;
                    }
                }
            } else {
                LDAPModification modification = new LDAPModification(LDAPModification.ADD,
                                                                     new LDAPAttribute(attrName, key + ";" + value));
                connection.modify(dn, modification);
                return;
            }
        } catch (LDAPException e) {
            throw new StorageException(  "Error setting credential for user '" + user.getName() + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public void removeUserCredential(User user, String key) throws StorageException {
        throw new IllegalStateException("credential handling is not yet implemented");
    }
    
    public void clearUserCredentials(User user) throws StorageException {
        throw new IllegalStateException("credential handling is not yet implemented");
    }
    
    public Role getRole(UserAdminFactory factory, String name) throws StorageException {
        LDAPConnection connection = openConnection();
        try {
            LDAPEntry entry = getEntryForName(connection, name);
//            LDAPEntry entry = readEntry(connection, name);
            return null != entry ? createRole(factory, entry) : null;
        } catch (LDAPException e) {
            throw new StorageException(  "Error finding role with name '" + name + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    public User getUser(UserAdminFactory factory, String key, String value) throws StorageException {
        LDAPConnection connection = openConnection();
        try {
            String filterString = "(&";
            for (String objectClass : m_userObjectclass.split(PATTERN_SPLIT_LIST_VALUE)) {
                filterString += "(" + ConfigurationConstants.ATTR_OBJECTCLASS + "=" + objectClass.trim() + ")";
            }
            filterString += "(" + key + "=" + value + "))";
            LDAPSearchResults result = connection.search(m_rootDN, LDAPConnection.SCOPE_SUB,
                                                         filterString, null, false);
            User user = null;
            while (result.hasMore()) {
                if (null != user) {
                    throw new StorageException("more than one user found");
                }
                LDAPEntry entry = result.next();
                Role role = createRole(factory, entry);
                if (null != role) {
                    if (Role.USER != role.getType()) {
                        throw new StorageException("Internal error: found role is not a user");
                    }
                    user = (User) role;
                }
            }
            return user;
        } catch (LDAPException e) {
            throw new StorageException(  "Error finding user with attribute '" + key + "=" + value + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
    
    /**
     * @see StorageProvider#findRoles(UserAdminFactory, String)
     */
    public Collection<Role> findRoles(UserAdminFactory factory, String filterString) throws StorageException {
        LDAPConnection connection = openConnection();
        Collection<Role> roles = new ArrayList<Role>();
        try {
            // TODO check
            LDAPSearchResults result = connection.search(m_rootUsersDN, LDAPConnection.SCOPE_ONE,
                                                         filterString, null, false);
            while (result.hasMore()) {
                LDAPEntry entry = result.next();
                Role role = createRole(factory, entry);
                if (null != role) {
                    roles.add(role);
                }
            }
            result = connection.search(m_rootGroupsDN, LDAPConnection.SCOPE_ONE,
                                                         filterString, null, false);
            while (result.hasMore()) {
                LDAPEntry entry = result.next();
                Role role = createRole(factory, entry);
                if (null != role) {
                    roles.add(role);
                }
            }
            return roles;
        } catch (LDAPException e) {
            throw new StorageException( "Error finding roles with filter '" + filterString + "': "
                                       + e.getMessage() + " / " + e.getLDAPErrorMessage());
        } finally {
            closeConnection();
        }
    }
}
