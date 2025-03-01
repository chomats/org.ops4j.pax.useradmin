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

import java.util.Map;

import org.ops4j.pax.useradmin.service.spi.StorageException;
import org.ops4j.pax.useradmin.service.spi.StorageProvider;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdminPermission;

/**
 * A dictionary to manage user credentials and communicate changes.
 *
 * @author Matthias Kuespert
 * @since  02.07.2009
 */
public class UserCredentials extends AbstractProperties {

    private static final long serialVersionUID = 1L;

    /**
     * Initializing constructor.
     *
     * @see AbstractProperties#AbstractProperties(Role, UserAdminUtil, Map)
     */
    protected UserCredentials(User user, UserAdminUtil util, Map<String, Object> properties) {
        super(user, util, properties);
    }

    /**
     * @return The owning role as user.
     */
    private User getUser() {
        return (User) getRole();
    }

    /**
     * @see AbstractProperties#checkGetPermission(String)
     */
    @Override
    protected void checkGetPermission(String key) {
        getUtil().checkPermission(key, UserAdminPermission.GET_CREDENTIAL);
    }

    /**
     * @see AbstractProperties#store(StorageProvider, String, Object)
     */
    @Override
    protected Object store(StorageProvider storageProvider, String key, Object plainValue)
        throws StorageException {
        getUtil().checkPermission(key, UserAdminPermission.CHANGE_CREDENTIAL);
        Object encryptedValue = getUtil().encrypt(plainValue);
//        System.out.println("enc = " + plainValue + "  - " + encryptedValue);
        storageProvider.setUserCredential(getUser(), key, encryptedValue);
        return encryptedValue;
    }

    /**
     * @see AbstractProperties#remove(StorageProvider, String)
     */
    @Override
    protected void remove(StorageProvider storageProvider, String key) throws StorageException {
        getUtil().checkPermission(key, UserAdminPermission.CHANGE_CREDENTIAL);
        storageProvider.removeUserCredential(getUser(), key);
    }

/*
 * Activate when OSGi finally moves to Map
 *
    @Override
    protected void clear(StorageProvider storageProvider) throws StorageException {
        for (Object key : keySet()) {
            getUtil().checkPermission((String) key, UserAdminPermission.CHANGE_CREDENTIAL);
        }
        storageProvider.clearUserCredentials(getUser());
    }
*/
}
