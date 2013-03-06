/**************************************************************************
 *
 * Gluewine Launcher Module
 *
 * Copyright (C) 2013 FKS bvba               http://www.fks.be/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version
 * 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 **************************************************************************/
package org.gluewine.launcher.sources;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.gluewine.launcher.CodeSource;

/**
 * Abstract implementation of CodeSource. It keeps track
 * of the common properties.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public abstract class AbstractCodeSource implements CodeSource
{
    // ===========================================================================
    /**
     * The classloader used.
     */
    private ClassLoader classLoader = null;

    /**
     * The display name.
     */
    private String displayName = null;

    /**
     * The list of enhancers.
     */
    private List<String> enhancers = new ArrayList<String>();

    /**
     * The list of entities.
     */
    private List<String> entities = new ArrayList<String>();

    /**
     * The list of services.
     */
    private List<String> services = new ArrayList<String>();

    /**
     * The type of source.
     */
    private String type = null;

    /**
     * The urls used.
     */
    private URL[] urls = null;

    // ===========================================================================
    /**
     * Creates an instance with the given urls.
     *
     * @param type The type of codesource.
     * @param urls The urls to use.
     */
    public AbstractCodeSource(String type, URL[] urls)
    {
        this.type = type;
        this.urls = urls;
    }

    // ===========================================================================
    /**
     * Adds an enhancer.
     *
     * @param enhancer The enhancer to add.
     */
    protected void addEnhancer(String enhancer)
    {
        enhancers.add(enhancer);
    }

    // ===========================================================================
    /**
     * Adds an entity.
     *
     * @param entity The entity to add.
     */
    protected void addEntity(String entity)
    {
        entities.add(entity);
    }

    // ===========================================================================
    /**
     * Adds a service.
     *
     * @param service The service to add.
     */
    protected void addService(String service)
    {
        services.add(service);
    }

    // ===========================================================================
    @Override
    public String getDisplayName()
    {
        return displayName;
    }

    // ===========================================================================
    @Override
    public String[] getEnhancers()
    {
        return enhancers.toArray(new String[enhancers.size()]);
    }

    // ===========================================================================
    @Override
    public String[] getEntities()
    {
        return entities.toArray(new String[entities.size()]);
    }

    // ===========================================================================
    @Override
    public String[] getServices()
    {
        return services.toArray(new String[services.size()]);
    }

    // ===========================================================================
    @Override
    public ClassLoader getSourceClassLoader()
    {
        return classLoader;
    }

    // ===========================================================================
    @Override
    public String getType()
    {
        return type;
    }

    // ===========================================================================
    @Override
    public URL[] getURLs()
    {
        return urls;
    }

    // ===========================================================================
    @Override
    public void setDisplayName(String name)
    {
        this.displayName = name;
    }

    // ===========================================================================
    @Override
    public void setSourceClassLoader(ClassLoader loader)
    {
        this.classLoader = loader;
    }

    // ===========================================================================
    @Override
    public void closeLoader()
    {
        try
        {
            if (classLoader instanceof URLClassLoader)
                ((URLClassLoader) classLoader).close();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }
}