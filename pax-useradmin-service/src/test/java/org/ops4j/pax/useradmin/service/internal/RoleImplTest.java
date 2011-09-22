/**
 * Copyright 2009 OPS4J
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ops4j.pax.useradmin.service.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.ops4j.pax.useradmin.service.spi.ExtendedRole.ImplicationResult;
import org.ops4j.pax.useradmin.service.spi.StorageException;
import org.ops4j.pax.useradmin.service.spi.StorageProvider;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.UserAdminEvent;
import org.osgi.service.useradmin.UserAdminPermission;

/**
 * Testing the RoleImpl and RoleProperties classes.
 * 
 * @author Matthias Kuespert
 * @since  11.07.2009
 */
public class RoleImplTest {

    private static final String NAME   = "someRole";
    private static final String KEY1   = "key1";
    private static final String VALUE1 = "someValue1";
    private static final String KEY2   = "key2";
    private static final byte[] VALUE2 = "someValue2".getBytes();
    
    private Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(KEY1, VALUE1);
        properties.put(KEY2, VALUE2);
        return properties;
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void constructNoName() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        new UserImpl(null, userAdmin, getProperties(), null);
        Assert.fail("No exception when creating role with invalid name");
    }

    @Test (expected = IllegalArgumentException.class)
    public void constructNoAdmin() {
        new UserImpl(NAME, null, getProperties(), null);
        Assert.fail("No exception when creating role with invalid UserAdmin instance");
    }

    @Test
    public void constructNullProperties() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        Assert.assertNotNull("Could not create RoleImpl instance", role);
        Assert.assertEquals("Mismatching name", NAME, role.getName());
        Assert.assertEquals("Invalid type", Role.USER, role.getType());
        Assert.assertEquals("Invalid UserAdmin instance", userAdmin, role.getAdmin());
        Assert.assertNotNull("Invalid properties", role.getProperties());
        Assert.assertEquals("Too many initial properties", 0, role.getProperties().size());
    }

    @Test
    public void constructEmptyProperties() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, new HashMap<String, Object>(), null);
        Assert.assertNotNull("Could not create RoleImpl instance", role);
        Assert.assertEquals("Mismatching name", NAME, role.getName());
        Assert.assertEquals("Invalid type", Role.USER, role.getType());
        Assert.assertEquals("Invalid UserAdmin instance", userAdmin, role.getAdmin());
        Assert.assertNotNull("Invalid properties", role.getProperties());
        Assert.assertEquals("Too many initial properties", 0, role.getProperties().size());
    }

    @Test
    @SuppressWarnings(value = "unchecked")
    public void constructOk() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, getProperties(), null);
        Assert.assertNotNull("Could not create RoleImpl instance", role);
        Assert.assertEquals("Mismatching name", NAME, role.getName());
        Assert.assertEquals("Invalid type", Role.USER, role.getType());
        Assert.assertEquals("Invalid UserAdmin instance", userAdmin, role.getAdmin());
        Dictionary properties = role.getProperties(); 
        Assert.assertNotNull(properties);
        Assert.assertNotNull("Invalid properties", properties);
        Assert.assertEquals("Mismatching property count", 2, properties.size());
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void getPropertyNullKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().get(null);
        //
        EasyMock.verify(userAdmin, sp);
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void getPropertyEmptyKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().get("");
        //
        EasyMock.verify(userAdmin, sp);
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void getPropertyWrongKeyType() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().get(666);
        //
        EasyMock.verify(userAdmin, sp);
    }
    
    @Test
    public void getPropertyOk() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, getProperties(), null);
        EasyMock.replay(userAdmin);
        //
        Assert.assertEquals("Mismatching value", VALUE1, role.getProperties().get(KEY1));
        Assert.assertEquals("Mismatching value", VALUE2, role.getProperties().get(KEY2));
        //
        EasyMock.verify(userAdmin);
    }
    
    @Test (expected = IllegalArgumentException.class)
    @SuppressWarnings(value = "unchecked")
    public void addPropertyNullKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().put(null, VALUE1);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    @SuppressWarnings(value = "unchecked")
    public void addPropertyEmptyKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().put("", VALUE1);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    @SuppressWarnings(value = "unchecked")
    public void addPropertyWrongKeyType() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().put(666, VALUE1);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    @SuppressWarnings(value = "unchecked")
    public void addPropertyNullValue() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().put(KEY1, null);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    @SuppressWarnings(value = "unchecked")
    public void addPropertyWrongValueType() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().put(KEY1, 666);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test
    @SuppressWarnings(value = "unchecked")
    public void addPropertyStorageException() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        StorageException exception = new StorageException("");
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY1, UserAdminPermission.CHANGE_PROPERTY);
            sp.setRoleAttribute(role, KEY1, VALUE1);
            EasyMock.expectLastCall().andThrow(exception);
            userAdmin.logMessage(EasyMock.isA(AbstractProperties.class),
                                 EasyMock.eq(LogService.LOG_ERROR),
                                 EasyMock.matches(exception.getMessage()));
            //
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY2, UserAdminPermission.CHANGE_PROPERTY);
            sp.setRoleAttribute(role, KEY2, VALUE2);
            EasyMock.expectLastCall().andThrow(exception);
            userAdmin.logMessage(EasyMock.isA(AbstractProperties.class),
                                 EasyMock.eq(LogService.LOG_ERROR),
                                 EasyMock.matches(exception.getMessage()));
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        Assert.assertNull("Setting property did return some previous value", role.getProperties().put(KEY1, VALUE1));
        Assert.assertNull("Setting property did return some previous value", role.getProperties().put(KEY2, VALUE2));
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test
    @SuppressWarnings(value = "unchecked")
    public void addPropertyOk() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            sp.setRoleAttribute(role, KEY1, VALUE1);
            userAdmin.checkPermission(KEY1, UserAdminPermission.CHANGE_PROPERTY);
            userAdmin.fireEvent(UserAdminEvent.ROLE_CHANGED, role);
            //
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            sp.setRoleAttribute(role, KEY2, VALUE2);
            userAdmin.checkPermission(KEY2, UserAdminPermission.CHANGE_PROPERTY);
            userAdmin.fireEvent(UserAdminEvent.ROLE_CHANGED, role);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        Assert.assertNull("Setting property did return some previous value", role.getProperties().put(KEY1, VALUE1));
        Assert.assertNull("Setting property did return some previous value", role.getProperties().put(KEY2, VALUE2));
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    public void removePropertyNullKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().remove(null);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    public void removePropertyEmptyKey() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().remove("");
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test (expected = IllegalArgumentException.class)
    public void removePropertyWrongKeyType() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, null, null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().remove(666);
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test
    public void removePropertyStorageException() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, getProperties(), null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        StorageException exception = new StorageException("");
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY1, UserAdminPermission.CHANGE_PROPERTY);
            sp.removeRoleAttribute(role, KEY1);
            EasyMock.expectLastCall().andThrow(exception);
            userAdmin.logMessage(EasyMock.isA(AbstractProperties.class),
                                 EasyMock.eq(LogService.LOG_ERROR),
                                 EasyMock.matches(exception.getMessage()));
            //
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY2, UserAdminPermission.CHANGE_PROPERTY);
            sp.removeRoleAttribute(role, KEY2);
            EasyMock.expectLastCall().andThrow(exception);
            userAdmin.logMessage(EasyMock.isA(AbstractProperties.class),
                                 EasyMock.eq(LogService.LOG_ERROR),
                                 EasyMock.matches(exception.getMessage()));
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        Assert.assertNull("Removing property did return some previous value", role.getProperties().remove(KEY1));
        Assert.assertNull("Removing property did return some previous value", role.getProperties().remove(KEY2));
        //
        EasyMock.verify(userAdmin, sp);
    }

    @Test
    public void removePropertyOk() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role = new UserImpl(NAME, userAdmin, getProperties(), null);
        StorageProvider sp = EasyMock.createMock(StorageProvider.class);
        //
        try {
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY1, UserAdminPermission.CHANGE_PROPERTY);
            sp.removeRoleAttribute(role, KEY1);
            userAdmin.fireEvent(UserAdminEvent.ROLE_CHANGED, role);
            //
            EasyMock.expect(userAdmin.getStorageProvider()).andReturn(sp);
            userAdmin.checkPermission(KEY2, UserAdminPermission.CHANGE_PROPERTY);
            sp.removeRoleAttribute(role, KEY2);
            userAdmin.fireEvent(UserAdminEvent.ROLE_CHANGED, role);
        } catch (StorageException e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
        EasyMock.replay(userAdmin, sp);
        //
        role.getProperties().remove(KEY1);
        role.getProperties().remove(KEY2);
        //
        EasyMock.verify(userAdmin, sp);
    }

    // Note: a test for the clear() method is not needed since the Dictionary
    // class does not provide a clear method

    @Test // (expected = IllegalStateException.class)
    public void impliedByOk() {
        UserAdminImpl userAdmin = EasyMock.createMock(UserAdminImpl.class);
        RoleImpl role1 = new UserImpl(NAME, userAdmin, getProperties(), null);
        RoleImpl role2 = new UserImpl(Role.USER_ANYONE, userAdmin, getProperties(), null);
        //
        EasyMock.replay(userAdmin);
        //
        Collection<Role> checkedRoles = new ArrayList<Role>();
        Assert.assertEquals("Role does not imply itself", ImplicationResult.IMPLIEDBY_YES, role1.isImpliedBy(role1, checkedRoles));
        Assert.assertEquals("Role does not imply user.anyone", ImplicationResult.IMPLIEDBY_YES, role2.isImpliedBy(role1, checkedRoles));
//        checkedRoles.clear();
//        Assert.assertEquals("", ImplicationResult.IMPLIEDBY_YES, role2.isImpliedBy(null, checkedRoles));
//        checkedRoles.clear();
//        checkedRoles.add(Role.USER_ANYONE);
//        Assert.assertEquals("", ImplicationResult.IMPLIEDBY_NO, role2.isImpliedBy(null, checkedRoles));
        //
        EasyMock.verify(userAdmin);
    }
}
