/**************************************************************************
 *
 * Gluewine Core Module
 *
 * Copyright (C) 2013 FKS bvba               http://www.fks.be/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ***************************************************************************/
package org.gluewine.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.gluewine.launcher.Launcher;
import org.gluewine.utils.ErrorLogger;


/**
 * Properties extension that accepts listeners to be registered.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class GluewineProperties extends Properties
{
    // ===========================================================================
    /**
     * The serial uid.
     */
    private static final long serialVersionUID = -3769089923730257060L;

    /**
     * The properties file name relative to the gluewine.cfgdir.
     */
    private String name = null;

    /**
     * The map of property files.
     */
    private static Map<String, WeakReference<GluewineProperties>> properties = new HashMap<String, WeakReference<GluewineProperties>>();

    /**
     * The method name to invoke on the owner when this file has been refreshed.
     */
    private String refreshMethod = null;

    /**
     * The object containing this Properties object.
     */
    private Object owner = null;

    // ===========================================================================
    /**
     * Creates an instance using the name specified. The name is the name of the
     * file relative to the gluewine.cfgdir.
     *
     * @param name The name of the file.
     * @param owner The owner of the file.
     */
    public GluewineProperties(String name, Object owner)
    {
        this(name, owner, null);
    }

    // ===========================================================================
    /**
     * Creates an instance using the name specified. The name is the name of the
     * file relative to the gluewine.cfgdir.
     *
     * @param name The name of the file.
     * @param owner The owning object.
     * @param refreshMethod The method to invoke when the file is refreshed.
     */
    public GluewineProperties(String name, Object owner, String refreshMethod)
    {
        this.name = name;
        this.owner = owner;
        this.refreshMethod = refreshMethod;

        properties.put(name, new WeakReference<GluewineProperties>(this));
    }

    // ===========================================================================
    /**
     * Refreshes the file.
     *
     * @throws IOException Thrown if an error occurs.
     */
    public void refresh() throws IOException
    {
        clear();
        load();

        if (refreshMethod != null && refreshMethod.trim().length() > 0 && owner != null)
        {
            AccessController.doPrivileged(new PrivilegedAction<Void>()
           {
                @Override
                public Void run()
                {
                    try
                    {
                        Method toInvoke = owner.getClass().getMethod(refreshMethod, new Class<?>[0]);
                        toInvoke.invoke(owner, new Object[0]);
                    }
                    catch (Throwable e)
                    {
                        ErrorLogger.log(getClass(), e);
                    }

                    return null;
                }
           });
        }
    }

    // ===========================================================================
    /**
     * Request the property file to be loaded.
     *
     * @throws IOException Thrown if an error occurs reading the file.
     */
    public void load() throws IOException
    {
        File f = new File(Launcher.getInstance().getConfigDirectory(), name);
        InputStream input = null;
        try
        {
            input = new FileInputStream(f);
            load(input);
        }
        finally
        {
            if (input != null) input.close();
        }
    }

    // ===========================================================================
    @Override
    public void load(InputStream input) throws IOException
    {
        clear();
        super.load(input);
    }

    // ===========================================================================
    @Override
    public void load(Reader reader) throws IOException
    {
        clear();
        super.load(reader);
    }

    // ===========================================================================
    @Override
    public void loadFromXML(InputStream input) throws IOException
    {
        clear();
        super.loadFromXML(input);
    }

    // ===========================================================================
    /**
     * Returns the active properties.
     *
     * @return The active properties.
     */
    public static Map<String, GluewineProperties> getActiveProperties()
    {
        Map<String, GluewineProperties> m = new HashMap<String, GluewineProperties>();

        for (Entry<String, WeakReference<GluewineProperties>> e : properties.entrySet())
        {
            GluewineProperties p = e.getValue().get();
            if (p != null) m.put(e.getKey(), p);
        }

        return m;
    }

    // ===========================================================================
    /**
     * Returns the class name of the owner of this properties.
     *
     * @return The owner class.
     */
    public String getOwnerClassName()
    {
        if (owner != null)
        {
            String name = owner.getClass().getName();
            int i = name.indexOf("$$EnhancerByCGLIB$$");
            if (i > -1) name = name.substring(0, i);
            return name;
        }
        else return null;
    }

    // ===========================================================================
    /**
     * Returns the name of the method to call when the file is refreshed.
     *
     * @return The method name.
     */
    public String getRefreshMethodName()
    {
        return refreshMethod;
    }

    // ===========================================================================
    @Override
    public boolean equals(Object g)
    {
        return super.equals(g);
    }

    // ===========================================================================
    @Override
    public int hashCode()
    {
        return super.hashCode();
    }
}
