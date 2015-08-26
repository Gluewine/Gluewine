/**************************************************************************
 *
 * Gluewine GXO Server Module
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
package org.gluewine.gxo_server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.gluewine.core.ContextInitializer;
import org.gluewine.core.Glue;
import org.gluewine.core.RepositoryListener;
import org.gluewine.core.RunOnActivate;
import org.gluewine.core.RunOnDeactivate;
import org.gluewine.core.RemoteCallValidator;
import org.gluewine.gxo.CloseBean;
import org.gluewine.gxo.CompressedBlockInputStream;
import org.gluewine.gxo.CompressedBlockOutputStream;
import org.gluewine.gxo.ExecBean;
import org.gluewine.gxo.GxoException;
import org.gluewine.gxo.InitBean;
import org.gluewine.gxo.LocalAccess;
import org.gluewine.sessions.SessionExpiredException;
import org.gluewine.sessions.SessionManager;
import org.gluewine.sessions.Unsecured;
import org.gluewine.utils.AnnotationUtility;
import org.gluewine.utils.ErrorLogger;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * Default implementation of the GxoServer.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class GxoServerImpl implements Runnable, GxoServer, RepositoryListener<Object>, XStreamConverterProvider
{
    // ===========================================================================
    /**
     * Converts SQL Dates.
     */
    private static class MySqlDateConverter extends DateConverter
    {
        /**
         * Creates an instance.
         */
        MySqlDateConverter()
        {
            super();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type)
        {
            boolean result = super.canConvert(type)
                || java.util.Date.class.isAssignableFrom(type);
            return result;
        }
    }

    /**
     * The set of registered instantiatable classes.
     */
    private Map<String, Class<?>> instantiatables = new HashMap<String, Class<?>>();

    /**
     * The set of registered method invocation validators.
     */
    private Set<RemoteCallValidator> validators = new HashSet<RemoteCallValidator>();

    /**
     * The set of registered xsteam converter providers.
     */
    private Set<XStreamConverterProvider> providers = new HashSet<XStreamConverterProvider>();

    /**
     * The logger instance to use.
     */
    private Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Max number of millis that the connection is allowed to be idle.
     */
    private int maxIdle = 300000;

    /**
     * The set of open sockets.
     */
    private Set<Socket> openSockets = new HashSet<Socket>();

    /**
     * The port the server is listening to.
     */
    private int port;

    /**
     * The socket of the server.
     */
    private ServerSocket server = null;

    /**
     * Map of available services. The services are indexed on their 'SimpleName',
     * ie. the name without package prefix.
     */
    private Map<String, Object> services = new HashMap<String, Object>();

    /**
     * Flag indicating that the server should stop.
     */
    private boolean stopRequested = false;

    /**
     * The XStream serializer/deserializer.
     */
    private XStream stream = null;

    /**
     * The session manager.
     */
    private SessionManager sessionManager;

    /**
     * The property file to use.
     */
    @Glue(properties = "gxo.properties", refresh = "propertiesChanged")
    private Properties properties;

    // ===========================================================================
    /**
     * Creates an instance.
     *
     * @throws Throwable If a problem occurs.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "SIC_INNER_SHOULD_BE_STATIC_ANON")
    public GxoServerImpl() throws Throwable
    {
        stream = new XStream(new StaxDriver());
    }

    // ===========================================================================
    /**
     * Deactivates the server and requests the server socket to be closed.
     * The server can no longer be used after this method has been invoked.
     */
    @RunOnDeactivate
    public void deactivate()
    {
        stopRequested = true;
        logger.debug("Stopping GXO Server.");
        if (server != null)
        {
           try
           {
               server.close();
           }
           catch (Throwable e)
           {
               logger.warn(e);
           }
        }
        logger.debug("GXO Server stop requested.");

        synchronized (openSockets)
        {
            for (Socket s : openSockets)
            {
                try
                {
                    s.close();
                }
                catch (Throwable e)
                {
                    logger.warn(e);
                }
            }
        }

        try
        {
            LocalAccess.getInstance().close();
        }
        catch (IOException e)
        {
            logger.warn(e);
        }
    }

    // ===========================================================================
    /**
     * Returns true if a stop has been requested.
     *
     * @return True if stop requested.
     */
    public boolean isStopRequested()
    {
        return stopRequested;
    }

    // ===========================================================================
    /**
     * Returns the set of interfaces implemented by the class (and all
     * of its parent classes).
     *
     * @param c The class to process.
     * @param set The set to update. If null a set is created.
     * @return The set of unique classnames.
     */
    private Set<Class<?>> getInterfaces(Class<?> c, Set<Class<?>> set)
    {
        if (set == null) set = new HashSet<Class<?>>();

        for (Class<?> interf : c.getInterfaces())
            set.add(interf);

        if (c.getSuperclass() != null)
            getInterfaces(c.getSuperclass(), set);

        return set;
    }

    // ===========================================================================
    @Override
    public XStream getXStream()
    {
        return stream;
    }

    // ===========================================================================
    /**
     * Initializes the server.
     *
     * @throws IOException If an error occurs reading the properties file.
     */
    @RunOnActivate
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_UNWRITTEN_FIELD")
    public void initialize() throws IOException
    {
        stopRequested = false;
        port = Integer.parseInt(properties.getProperty("port", "1966"));
        maxIdle = Integer.parseInt(properties.getProperty("maxidle", "300")) * 1000;
        Thread th = new Thread(this, "GXO Server Thread");
        th.start();
    }

    // ===========================================================================
    /**
     * Processes local requests. Local requests are passed on a named pipe.
     */
    @RunOnActivate(runThreaded = true)
    public void processLocalRequests()
    {
        // The map containing the instantiated services.
        Map<String, Object> instantiated = new HashMap<String, Object>();

        try
        {
            LocalAccess la = LocalAccess.getInstance();
            InputStreamReader in = new InputStreamReader(la.getClientInputStream(), "UTF-8");
            OutputStreamWriter out = new OutputStreamWriter(la.getClientOutputStream(), "UTF-8");

            while (!stopRequested)
            {
                try
                {
                    Object ob = stream.fromXML(in);

                    if (ob instanceof ExecBean) processExecBean(out, instantiated, (ExecBean) ob);
                    else if (ob instanceof InitBean) processInitBean(out, instantiated, (InitBean) ob);
                }
                catch (Throwable e)
                {
                    if (la.isClosed())
                    {
                        in = new InputStreamReader(la.getClientInputStream(), "UTF-8");
                        out = new OutputStreamWriter(la.getClientOutputStream(), "UTF-8");
                    }

                    else if (!stopRequested)
                        la.close();
                }
            }
        }
        catch (Throwable e)
        {
            ErrorLogger.log(getClass(), e);
        }
    }

    // ===========================================================================
    /**
     * Processes the given exec bean and writes the result on the given output stream.
     * The map contains the list of objects that were instantiated in this session.
     *
     * @param out The output stream to write to.
     * @param instantiated The map of instantiated objects.
     * @param bean The bean to process.
     * @throws IOException Thrown if an error occurs writing back to the caller.
     */
    public void processExecBean(OutputStreamWriter out, Map<String, Object> instantiated, ExecBean bean) throws IOException
    {
        try
        {
            processExecBean(instantiated, bean, out);
        }
        catch (SessionExpiredException e)
        {
            GxoException ge = new GxoException(e);
            stream.toXML(ge, out);
        }
        catch (Throwable e)
        {
            ErrorLogger.log(getClass(), e);
            GxoException ge = new GxoException(toRegularException(e));
            stream.toXML(ge, out);
        }
        out.flush();
    }

    // ===========================================================================
    /**
     * Processes the given init bean and writes the result on the given output stream.
     * The map contains the list of objects already instantiated.
     *
     * @param out The output stream to write to.
     * @param instantiated The map of instantiated objects.
     * @param bean The bean to process.
     * @throws IOException Thrown if an error occurs writing back to the caller.
     */
    @ContextInitializer
    public void processInitBean(OutputStreamWriter out, Map<String, Object> instantiated, InitBean bean) throws IOException
    {
        try
        {
            Object result = processInitBean(instantiated, bean);
            stream.toXML(result, out);
        }
        catch (Throwable e)
        {
            ErrorLogger.log(getClass(), e);
            GxoException ge = new GxoException(toRegularException(e));
            stream.toXML(ge, out);
        }
        out.flush();
    }

    // ===========================================================================
    /**
     * Processes a socket. This will read ExecBeans and dispatch them
     * to the right service, until a CloseBean is received or the socket is closed.
     *
     * @param socket The socket to process.
     */
    private void process(Socket socket)
    {
        CompressedBlockInputStream cin = null;
        CompressedBlockOutputStream cout = null;
        try
        {
            socket.setKeepAlive(true);
            cin = new CompressedBlockInputStream(socket.getInputStream());
            cout = new CompressedBlockOutputStream(socket.getOutputStream(), 1024);
            InputStreamReader in = new InputStreamReader(cin, "UTF-8");
            OutputStreamWriter out = new OutputStreamWriter(cout, "UTF-8");

            // The map containing the instantiated services.
            Map<String, Object> instantiated = new HashMap<String, Object>();
            if (maxIdle > 0) socket.setSoTimeout(maxIdle);

            while (true)
            {
                Object ob = stream.fromXML(in);

                if (ob instanceof ExecBean) processExecBean(out, instantiated, (ExecBean) ob);

                else if (ob instanceof InitBean) processInitBean(out, instantiated, (InitBean) ob);

                else if (ob instanceof CloseBean)
                {
                    if (logger.isDebugEnabled())
                        logger.debug("Closing socket");

                    socket.close();
                    socket = null;
                    return;
                }
            }
        }
        catch (Throwable e)
        {
            if (e.getMessage() == null || (e.getMessage().indexOf("Read timed out") < 0 && e.getMessage().indexOf("Connection reset") < 0) && e.getMessage().indexOf("ParseError at [row,col]:[1,1]") < 0)
                ErrorLogger.log(getClass(), e);
        }

        finally
        {
            if (socket != null)
            {
                try
                {
                    socket.close();
                }
                catch (Throwable e)
                {
                    logger.warn(e);
                }
                synchronized (openSockets)
                {
                    openSockets.remove(socket);
                }
            }
        }
    }

    // ===========================================================================
    /**
     * Processes the exec bean specified and returns the output of the command.
     *
     * @param instantiated The map of services instantiated in this session.
     * @param bean The bean to process.
     * @param out the output stream to serialise the result to.
     */
    private void processExecBean(Map<String, Object> instantiated, ExecBean bean, OutputStreamWriter out)
    {
        if (logger.isDebugEnabled())
            logger.debug("Executing " + bean.getService() + ":" + bean.getMethod());

        Object o = instantiated.get(bean.getService());
        if (o == null) o = services.get(bean.getService());

        if (o != null)
        {
            try
            {
                Method m = o.getClass().getMethod(bean.getMethod(), bean.getParamTypes());

                if (AnnotationUtility.getAnnotation(Unsecured.class, m, o) == null)
                {
                    if (sessionManager != null)
                    {
                        sessionManager.setCurrentSessionId(bean.getSessionId());
                        sessionManager.checkAndTickSession(bean.getSessionId());
                    }
                }

                for (RemoteCallValidator validator : validators)
                    validator.validateCall("GXO", o, m, bean.getParams());

                invokeMethod(out, m, o, bean.getParams());
                if (logger.isTraceEnabled())
                    logger.trace(bean.getService() + ":" + bean.getMethod() + " finished");
            }
            catch (SessionExpiredException e)
            {
                ErrorLogger.log(getClass(), e);
                throw e;
            }
            catch (InvocationTargetException e)
            {
                ErrorLogger.log(getClass(), e);
                GxoException ge = new GxoException(toRegularException(e.getCause()));
                stream.toXML(ge, out);
            }
            catch (Throwable e)
            {
                ErrorLogger.log(getClass(), e);
                GxoException ge = new GxoException(toRegularException(e));
                stream.toXML(ge, out);
            }
        }
        else
        {
            logger.warn("Undefined service " + bean.getService());
            GxoException ge = new GxoException("Undefined service " + bean.getService());
            stream.toXML(ge, out);
        }
    }

    /**
     * Calls a method and serialises the output.
     * @param out the stream to write to.
     * @param m the method to call.
     * @param o the object to call the method on.
     * @param params the method parameters.
     * @throws InvocationTargetException if the call fails.
     * @throws IllegalAccessException if the call fails.
     */
    @ContextInitializer
    public void invokeMethod(OutputStreamWriter out, Method m, Object o, Object[] params) throws InvocationTargetException, IllegalAccessException
    {
        Object result = m.invoke(o, params);
        stream.toXML(result, out);
    }

    // ===========================================================================
    /**
     * Processes the init bean specified and returns the result.
     *
     * @param instantiated The map to update.
     * @param bean The bean to process.
     * @return The result.
     */
    private Object processInitBean(Map<String, Object> instantiated, InitBean bean)
    {
        Object result = null;
        if (logger.isDebugEnabled()) logger.debug("Received init request for " + bean.getClassName());
        if (instantiatables.containsKey(bean.getClassName()))
        {
            Class<?> cl = instantiatables.get(bean.getClassName());
            Object instance = null;
            try
            {
                if (bean.getParamTypes() == null || bean.getParamTypes().length == 0)
                    instance = cl.newInstance();

                else
                {
                    if (logger.isDebugEnabled())
                    {
                        StringBuilder b = new StringBuilder("Looking for constructor: ");
                        for (Class<?> c : bean.getParamTypes())
                            b.append(c.getName()).append(" ");
                        logger.debug(b.toString().trim());
                    }
                    Constructor<?> c = cl.getConstructor(bean.getParamTypes());
                    instance = c.newInstance(bean.getParamValues());
                }

                if (instance != null)
                {
                    String id = UUID.randomUUID().toString();
                    if (logger.isDebugEnabled()) logger.debug("Registering instance with id " + id);
                    instantiated.put(id, instance);
                    result = id;
                }
                else
                    result = new GxoException("Could not instantiate " + cl.getName());
            }
            catch (Throwable e)
            {
                ErrorLogger.log(getClass(), e);
                result = toRegularException(e);
            }

        }
        else
            result = new GxoException("Undefined instantiatable " + bean.getClassName());

        return result;
    }

    // ===========================================================================
    @Override
    public void registered(Object o)
    {
        if (o instanceof XStreamProvider)
            initializeStream((XStreamProvider) o);

        if (o instanceof XStreamConverterProvider)
        {
            ((XStreamConverterProvider) o).registerConverters(stream);
            providers.add((XStreamConverterProvider) o);
        }

        Set<Class<?>> interfs = getInterfaces(o.getClass(), null);
        synchronized (services)
        {
            for (Class<?> interf : interfs)
                services.put(interf.getName(), o);
        }

        if (o instanceof RemoteCallValidator)
            validators.add((RemoteCallValidator) o);

        if (o instanceof SessionManager)
            sessionManager = (SessionManager) o;
    }

    // ===========================================================================
    /**
     * Initializes a new XStream using the given provider.
     *
     * @param provider The provider to use.
     */
    private void initializeStream(XStreamProvider provider)
    {
        stream = provider.getXStream();
        // Reregister all available converters:
        for (XStreamConverterProvider prov : providers)
            prov.registerConverters(stream);
    }

    // ===========================================================================
    @Override
    public void run()
    {
        logger.info("Starting GXO Server on port: " + port);
        while (!stopRequested)
        {
            try
            {
                server = new ServerSocket(port);
                //server.setSoTimeout(1000);
                while (!stopRequested)
                {
                    try
                    {
                        final Socket socket = server.accept();
                        synchronized (openSockets)
                        {
                            openSockets.add(socket);
                        }
                        Thread thread = new Thread()
                        {
                            public void run()
                            {
                                if (logger.isDebugEnabled())
                                    logger.debug("Accepting incoming session from " + socket.getRemoteSocketAddress().toString());
                                try
                                {
                                    process(socket);
                                }
                                catch (Throwable e)
                                {
                                    ErrorLogger.log(GxoServerImpl.class, e);
                                }
                            }
                        };
                        thread.start();
                    }
                    catch (SocketTimeoutException e)
                    {
                        // allowed to fail/
                    }
                    catch (Throwable e)
                    {
                        if (!stopRequested)
                            ErrorLogger.log(getClass(), e);
                    }
                }
            }
            catch (Throwable e)
            {
                if (!stopRequested)
                {
                    ErrorLogger.log(getClass(), e);
                    try
                    {
                        Thread.sleep(5000);
                    }
                    catch (Throwable t)
                    {
                        logger.warn(t);
                    }
                }
            }
        }
        logger.info("GXOServer stopped");
    }

    // ===========================================================================
    @Override
    public void setInstantiatableService(Class<?> cl)
    {
        Set<Class<?>> interfaces = getInterfaces(cl, null);
        for (Class<?> interf : interfaces)
            instantiatables.put(interf.getName(), cl);
    }

    // ===========================================================================
    /**
     * Converts the throwable given to a regular Throwable.
     * @param e The throwable to convert.
     * @return The converted Throwable.
     */
    private Throwable toRegularException(Throwable e)
    {
        Throwable n = new Throwable(e.getClass().getName() + ": " + e.getMessage());
        if (e.getCause() != null)
            n.initCause(toRegularException(e.getCause()));

        n.setStackTrace(e.getStackTrace());

        return n;
    }

    // ===========================================================================
    @Override
    public void unregistered(Object o)
    {
        if (o instanceof XStreamConverterProvider)
            providers.remove((XStreamConverterProvider) o);

        Set<Class<?>> interfs = getInterfaces(o.getClass(), null);
        synchronized (services)
        {
            for (Class<?> interf : interfs)
                services.remove(interf.getName());
        }

        if (o instanceof RemoteCallValidator)
            validators.remove((RemoteCallValidator) o);
    }

    // ===========================================================================
    @Override
    public void unsetInstantiatableService(Class<?> cl)
    {
        Set<Class<?>> interfaces = getInterfaces(cl, null);
        for (Class<?> interf : interfaces)
            instantiatables.remove(interf.getName());
    }

    // ===========================================================================
    @Override
    public void registerConverters(XStream stream)
    {
        stream.registerConverter(new MySqlDateConverter());

        stream.alias("date", java.sql.Date.class, java.util.Date.class);
        stream.alias("date", java.sql.Time.class, java.util.Date.class);
        stream.alias("date", java.sql.Timestamp.class, java.util.Date.class);

        final Class<?> emptyListType = Collections.EMPTY_LIST.getClass();
        stream.alias(emptyListType.getName(), emptyListType);

        stream.registerConverter(new ReflectionConverter(stream.getMapper(), stream.getReflectionProvider())
        {
            @Override
            @SuppressWarnings("rawtypes")
            public boolean canConvert(Class type)
            {
                return type == emptyListType;
            }
        });
    }

    // ===========================================================================
    /**
     * Invoked when the properties have changed.
     */
    public void propertiesChanged()
    {
        deactivate();
        try
        {
            initialize();
        }
        catch (IOException e)
        {
            ErrorLogger.log(getClass(), e);
        }
    }
}
