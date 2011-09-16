package org.ops4j.pax.useradmin.service.spi;

import java.util.Collection;

import org.osgi.service.useradmin.Role;

public interface ExtendedRole extends Role {
    
    /**
     * States used as return value for isImpliedBy() calls.
     */
    enum ImplicationResult {
        IMPLIEDBY_YES,           // given role is implied by this one
        IMPLIEDBY_NO,            // given role is not implied by this one
        IMPLIEDBY_LOOPDETECTED   // detected a loop - e.g. a group containing itself.
    }
    
    /**
     * Checks if this role is implied by the given one.
     * 
     * @param role The role to check.
     * @param checkedRoles Used for loop detection.
     * @return An <code>ImplicationResult</code>.
     */
    ImplicationResult isImpliedBy(Role role, Collection<String> checkedRoles);
}
