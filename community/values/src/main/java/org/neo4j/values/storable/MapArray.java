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
package org.neo4j.values.storable;

import org.neo4j.values.AnyValue;
import org.neo4j.values.ValueMapper;
import org.neo4j.values.virtual.MapValue;

import java.util.Arrays;
import java.util.Map;

// TODO complete the implementation
public class MapArray extends ArrayValue
{
    private Map<String, AnyValue>[] values;

    public MapArray( Map<String, AnyValue>[] values )
    {
        this.values = values;
    }

    @Override
    public int length()
    {
        System.err.println("MapArray.length()");
        return values.length;
    }

    @Override
    public Object asObjectCopy()
    {
        System.err.println("MapArray.asObjectCopy()");
        return values.clone();
    }

    @Override
    public String prettyPrint()
    {
        System.err.println("MapArray.prettyPrint()");
        return values.toString();
    }

    @Override
    public NumberType numberType()
    {
        System.err.println("MapArray.numberType()");
        return NumberType.NO_NUMBER;
    }

    @Override
    protected int computeHash()
    {
        System.err.println("MapArray.computeHash()");
        return Arrays.hashCode( values );
    }

    @Override
    public AnyValue value( int offset )
    {
        System.err.println("MapArray.value(" + offset + ")");
        return new MapValue( values[offset] );
    }

    @Override
    public boolean equals( Value other )
    {
        System.err.println("MapArray.equals(" + other + ")");
        return other.equals( values );
    }

    @Override
    int unsafeCompareTo( Value other )
    {
        System.err.println("MapArray.unsafeCompareTo(" + other + ")");
        return 0;
    }

    @Override
    public <E extends Exception> void writeTo( ValueWriter<E> writer ) throws E
    {
        System.err.println("MapArray.writeTo(" + writer.getClass() + ")");
    }

    @Override
    public ValueGroup valueGroup()
    {
        System.err.println("MapArray.valueGroup()");
        return ValueGroup.UNKNOWN;
    }

    @Override
    public <T> T map( ValueMapper<T> mapper )
    {
        System.err.println("MapArray.map(" + mapper.getClass() + ")");
        return null;
    }
}
