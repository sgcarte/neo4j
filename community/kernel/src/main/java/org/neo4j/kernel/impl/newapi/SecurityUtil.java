/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.newapi;

import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.exceptions.PropertyKeyIdNotFoundKernelException;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.logging.Log;
import org.neo4j.values.storable.TextValue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SecurityUtil
{
    public static boolean hasAccess( PropertyCursor properties, Read read )
    {
        while ( properties.next() )
        {
            String propertyKeyName;
            try
            {
                propertyKeyName = read.ktx.tokenRead().propertyKeyName(properties.propertyKey());
            }
            catch ( PropertyKeyIdNotFoundKernelException e )
            {
                return true;
            }
            if ( propertyKeyName.equals("markup") )
            {
                return read.ktx.securityContext().hasAccess(((TextValue) properties.propertyValue()).stringValue());
            }
        }
        return true;
    }
}
