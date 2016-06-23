package org.gluewine.gluedgwt;

import com.google.gwt.user.server.rpc.impl.TypeNameObfuscator;
import com.google.gwt.user.server.rpc.impl.StandardSerializationPolicy;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import java.util.Set;
import java.util.HashMap;

/**
 * SerializationPolicy wrapper that supports type aliases.
 *
 * @author fks/Frank Gevaerts
 */
public class GluewineSerializationPolicy extends SerializationPolicy implements TypeNameObfuscator
{
    /** The parent policy. */
    private StandardSerializationPolicy parent;

    /** The alias table. */
    private static HashMap<Class<?>, Class<?>> aliases = new HashMap<Class<?>, Class<?>>();

    static
    {
        addAlias(org.hibernate.collection.internal.PersistentSet.class, java.util.HashSet.class);
        addAlias(org.hibernate.collection.internal.PersistentList.class, java.util.ArrayList.class);
        addAlias(org.hibernate.collection.internal.PersistentBag.class, java.util.ArrayList.class);
        addAlias(org.hibernate.collection.internal.PersistentMap.class, java.util.HashMap.class);
        addAlias(org.hibernate.collection.internal.PersistentSortedMap.class, java.util.TreeMap.class);
        addAlias(org.hibernate.collection.internal.PersistentSortedSet.class, java.util.TreeSet.class);
    }

    /**
     * Add an alias.
     * @param alias the alias to add
     * @param target the class the alias should be mapped to
     */
    public static void addAlias(Class<?> alias, Class<?> target)
    {
        aliases.put(alias, target);
    }

    /**
     * Constructs a GluewineSerializationPolicy.
     * @param parent the parent to use.
     */
    public GluewineSerializationPolicy(StandardSerializationPolicy parent)
    {
        this.parent = parent;
    }

    /**
     * Substitutes an alias if needed. TODO: add all hibernate types
     * @param clazz the class to look at.
     * @return the actual class to use.
     */
    private Class<?> substitute(Class<?> clazz)
    {
        System.out.print("Looking for " + clazz + " ... ");
        Class<?> alias = aliases.get(clazz);
        if (alias != null)
        {
            System.out.print(" Match: " + alias + " ... ");
            clazz = alias;
        }
        System.out.println("Found " + clazz);

        return clazz;
    }

    @Override
    public final String getTypeIdForClass(Class<?> clazz) throws SerializationException
    {
        return parent.getTypeIdForClass(substitute(clazz));
    }

    @Override
    public final String getClassNameForTypeId(String id) throws SerializationException
    {
        return parent.getClassNameForTypeId(id);
    }


    @Override
    public void validateSerialize(Class<?> clazz) throws SerializationException
    {
        parent.validateSerialize(substitute(clazz));
    }

    @Override
    public void validateDeserialize(Class<?> clazz) throws SerializationException
    {
        parent.validateDeserialize(substitute(clazz));
    }

    @Override
    public boolean shouldSerializeFields(Class<?> clazz)
    {
        return parent.shouldSerializeFields(substitute(clazz));
    }

    @Override
    public boolean shouldDeserializeFields(Class<?> clazz)
    {
        return parent.shouldDeserializeFields(substitute(clazz));
    }

    @Override
    public Set<String> getClientFieldNamesForEnhancedClass(Class<?> clazz)
    {
        return parent.getClientFieldNamesForEnhancedClass(substitute(clazz));
    }

}
