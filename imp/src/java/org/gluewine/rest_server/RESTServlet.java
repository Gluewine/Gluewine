/**************************************************************************
 *
 * Gluewine REST Server Module
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
package org.gluewine.rest_server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.gluewine.authentication.AuthenticationException;
import org.gluewine.core.RepositoryListener;
import org.gluewine.core.ContextInitializer;
import org.gluewine.jetty.GluewineServlet;
import org.gluewine.rest.REST;
import org.gluewine.rest.RESTID;
import org.gluewine.rest.RESTMethod;
import org.gluewine.rest.RESTSerializer;
import org.gluewine.sessions.SessionManager;
import org.gluewine.sessions.Unsecured;
import org.gluewine.utils.AnnotationUtility;
import org.gluewine.utils.ErrorLogger;

/**
 * Handles all REST requests, and dispatches them to the correct objects.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class RESTServlet extends GluewineServlet implements RepositoryListener<Object>
{
    // ===========================================================================
    /**
     * The serial uid.
     */
    private static final long serialVersionUID = -5416068629723455281L;

    /**
     * The map of all registered serializers.
     */
    private Map<String, RESTSerializer> serializers = new HashMap<String, RESTSerializer>();

    /**
     * Map of registerd, annotated, methods.
     */
    private Map<String, RESTMethod> methods = new HashMap<String, RESTMethod>();

    /**
     * The list of available authenticators.
     */
    private List<RESTAuthenticator> authenticators = new ArrayList<RESTAuthenticator>();

    /**
     * The session manager to use.
     */
    private SessionManager sessionManager;

    /**
     * The logger instance to use.
     */
    private Logger logger = Logger.getLogger(getClass());

    /**
     * Bean holding the current request and response.
     */
    private static class RESTBean
    {
        /**
         * The current request.
         */
        private HttpServletRequest request;
        /**
         * The current response.
         */
        private HttpServletResponse response;
    }

    /**
     * The map of beans bound with the current thread.
     */
    private static Map<Thread, RESTBean> beans = new HashMap<Thread, RESTBean>();

    // ===========================================================================
    @Override
    public String getContextPath()
    {
        return "REST";
    }

    // ===========================================================================
    /**
     * Returns the JSon context parsed from the request uri.
     *
     * @param req The request to process.
     * @return The json context.
     */
    private String getRESTPath(HttpServletRequest req)
    {
        String base = "/REST/";

        // Check the uri:
        String uri = req.getRequestURI();
        if (uri.length() > base.length())
        {
            String path = uri.substring(base.length());
            int i = path.indexOf('?');
            if (i > -1) path = path.substring(0, i);
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return path;
        }
        else
            return "";
    }

    // ===========================================================================
    /**
     * Performs an authentication. If all available authenticators fail to authenticate
     * the request, a 401 (unauthorized) is send with a header WWW-Authenticate set to
     * Basic.
     *
     * @param req The current request.
     * @param resp The current response.
     * @param service The service where the method is called.
     * @throws AuthenticationException Throw if none of the available authenticators succeeded.
     */
    private void authenticate(HttpServletRequest req, HttpServletResponse resp, Object service) throws AuthenticationException
    {
        req.setAttribute(RESTAuthenticator.CALLED_SERVICE, service);

        for (RESTAuthenticator auth : authenticators)
        {
            try
            {
                String session = auth.authenticate(req, resp);
                if (sessionManager != null) sessionManager.setCurrentSessionId(session);
                return;
            }
            catch (AuthenticationException e)
            {
                // Ignore, as we will check ALL available authenticators.
            }
        }
        if (authenticators.size() > 0) throw new AuthenticationException("Authentication Required");
    }

    // ===========================================================================
    /**
     * Parses the form stored in the given request, and returns a map containing
     * all FileItems indexed on their name.
     *
     * @param req The request to parse.
     * @return The map of items.
     * @throws IOException If an error occurs.
     */
    private Map<String, FileItem> parseForm(HttpServletRequest req) throws IOException
    {
        Map<String, FileItem> formFields = new HashMap<String, FileItem>();
        try
        {
            FileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);
            for (FileItem item : items)
                formFields.put(item.getFieldName(), item);
        }
        catch (FileUploadException e)
        {
            throw new IOException(e.getMessage());
        }

        return formFields;
    }

    // ===========================================================================
    /**
     * Initialises the parameter values for the given method parsed from the request.
     *
     * @param rm The method to process.
     * @param req The request containing the parameters.
     * @param resp The servlet response.
     * @param params the parameter value array to fill in.
     * @param paramTypes the parameter types array.
     * @throws IOException If an error occurs.
     */
    private void initParamValues(RESTMethod rm, HttpServletRequest req, HttpServletResponse resp, Object[] params, Class<?>[] paramTypes) throws IOException
    {
        for (int i = 0; i < params.length; i++)
        {
            params[i] = null;
            RESTID id = AnnotationUtility.getAnnotations(RESTID.class, rm.getObject(), rm.getMethod(), i);
            if (id != null)
            {
                if (id.header())
                {
                    params[i] = req.getHeader(id.id());
                }
                else if (id.method())
                {
                    params[i] = req.getMethod();
                }
            }
            else if (HttpServletResponse.class.isAssignableFrom(paramTypes[i]))
            {
                params[i] = resp;
            }
            else if (HttpServletRequest.class.isAssignableFrom(paramTypes[i]))
            {
                params[i] = req;
            }
        }
    }
    /**
     * Returns the parameter values for the given method parsed from the request.
     *
     * @param rm The method to process.
     * @param req The request containing the parameters.
     * @param serializer The serializer to use.
     * @param params the parameter value array to fill in.
     * @param paramTypes the parameter types array.
     * @throws IOException If an error occurs.
     */
    private void fillParamValuesFromRequest(RESTMethod rm, HttpServletRequest req, RESTSerializer serializer, Object[] params, Class<?>[] paramTypes) throws IOException
    {
        for (int i = 0; i < params.length; i++)
        {
            if (params[i] != null)
                continue;

            RESTID id = AnnotationUtility.getAnnotations(RESTID.class, rm.getObject(), rm.getMethod(), i);
            if (id != null)
            {
                String[] val;
                if (id.body())
                {
                    val = new String[1];
                    if (InputStream.class.isAssignableFrom(paramTypes[i]))
                    {
                        params[i] = req.getInputStream();
                    }
                    else
                    {
                        byte[] bytes = IOUtils.toByteArray(req.getInputStream());
                        val[0] = new String(bytes, "UTF-8");
                    }
                }
                else
                {
                    val = req.getParameterValues(id.id());
                }
                if (logger.isTraceEnabled()) traceParameter(id.id(), val);
                if (params[i] == null && val != null && val.length > 0) params[i] = serializer.deserialize(paramTypes[i], val);
            }
        }
    }

    // ===========================================================================
    /**
     * Parses the parameters from the form stored in the given map.
     *
     * @param rm The method to process.
     * @param formFields The fields to parse.
     * @param serializer The serializer to use.
     * @param params the parameter value array to fill in.
     * @param paramTypes the parameter types array.
     * @throws IOException If an error occurs.
     */
    private void fillParamValuesFromForm(RESTMethod rm, Map<String, FileItem> formFields, RESTSerializer serializer, Object[] params, Class<?>[] paramTypes) throws IOException
    {
        for (int i = 0; i < params.length; i++)
        {
            if (params[i] != null)
                continue;

            RESTID id = AnnotationUtility.getAnnotations(RESTID.class, rm.getObject(), rm.getMethod(), i);
            if (id != null)
            {
                FileItem item = formFields.get(id.id());
                if (item != null)
                {
                    String[] val = null;
                    if (item.isFormField())
                    {
                        val = new String[] {item.getString("UTF-8")};
                        params[i] = serializer.deserialize(paramTypes[i], val);
                        if (logger.isTraceEnabled()) traceParameter(id.id(), val);
                    }
                    else
                    {
                        if (id.mimetype())
                        {
                            params[i] = item.getContentType();
                        }
                        else if (id.filename())
                        {
                            params[i] = item.getName();
                        }
                        else
                        {
                            try
                            {
                                if (InputStream.class.isAssignableFrom(paramTypes[i]))
                                {
                                    params[i] = item.getInputStream();
                                }
                                else
                                {
                                    File nf = new File(item.getName());
                                    File f = File.createTempFile("___", "___" + nf.getName());
                                    item.write(f);
                                    if (File.class.isAssignableFrom(paramTypes[i]))
                                    {
                                        params[i] = f;
                                    }
                                    else
                                    {
                                        logger.warn("File upload to string field for method " + rm.getMethod());
                                        params[i] = f.getAbsolutePath();
                                    }
                                }
                            }
                            catch (Exception e)
                            {
                                throw new IOException(e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    // ===========================================================================
    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        try
        {
            RESTBean rb = new RESTBean();
            rb.request = req;
            rb.response = resp;
            beans.put(Thread.currentThread(), rb);

            String path = getRESTPath(req);
            if (logger.isDebugEnabled()) logger.debug("Received REST request for : " + path);
            if (methods.containsKey(path))
            {
                RESTMethod rm = methods.get(path);
                Map<String, FileItem> formFields = null;
                if (rm.isForm()) formFields = parseForm(req);

                String format = null;
                if (rm.isForm())
                {
                    FileItem item = formFields.get("format");
                    if (item != null) format = item.getString("UTF-8");
                }
                else format = req.getParameter("format");

                if (format == null) format = "json";
                RESTSerializer serializer = serializers.get(format);
                if (serializer != null)
                {

                    if (AnnotationUtility.getAnnotation(Unsecured.class, rm.getMethod(), rm.getObject()) == null)
                        authenticate(req, resp, rm.getObject());

                    Class<?>[] paramTypes = rm.getMethod().getParameterTypes();
                    Object[] params = new Object[paramTypes.length];

                    initParamValues(rm, req, resp, params, paramTypes);

                    if (rm.isForm())
                        fillParamValuesFromForm(rm, formFields, serializer, params, paramTypes);
                    else
                        fillParamValuesFromRequest(rm, req, serializer, params, paramTypes);

                    try
                    {
                        if (rm.getMethod().getReturnType().equals(Void.TYPE) || rm.getMethod().getReturnType().equals(InputStream.class)
                                || rm.getMethod().getReturnType().equals(OutputStream.class))
                            executeMethod(rm, params);

                        else
                        {
                            String s = executeMethod(rm, params, serializer);
                            if (logger.isTraceEnabled()) logger.trace("Serialized response: " + s);
                            if (resp.getContentType() == null)
                            {
                                resp.setContentType(serializer.getResponseMIME());
                                resp.setCharacterEncoding("utf8");
                            }
                            resp.getWriter().write(s);
                            resp.getWriter().flush();
                            resp.getWriter().close();
                        }
                    }
                    catch (IOException e)
                    {
                        ErrorLogger.log(getClass(), e);
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Method execution failed: " + e.getMessage());
                    }
                }
                else resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unsupported format");
            }
            else
            {
                logger.warn("Request for unavailable REST method " + path);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "There is no method registered with path " + path);
            }
        }
        catch (AuthenticationException e)
        {
            resp.setHeader("WWW-Authenticate", "BASIC realm=\"SecureFiles\"");
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please provide username and password");
        }
        finally
        {
            if (sessionManager != null) sessionManager.clearCurrentSessionId();
            beans.remove(Thread.currentThread());
        }
    }

    // ===========================================================================
    /**
     * Executes the given method using the provided parameters, and returns the result
     * serialized with the given serializer.
     *
     * @param rm The method to execute.
     * @param params The paramters to use.
     * @param serializer The serializer to use.
     * @return The serialized result.
     * @throws IOException If the method failed execution.
     */
    @ContextInitializer
    public String executeMethod(RESTMethod rm, Object[] params, RESTSerializer serializer) throws IOException
    {
        Object result = executeMethod(rm, params);
        return serializer.serialize(result);
    }

    // ===========================================================================
    /**
     * Traces the parameter given.
     *
     * @param name The name of the parameter.
     * @param val The value of the parameter.
     */
    private void traceParameter(String name, String[] val)
    {
        StringBuilder b = new StringBuilder();
        for (String v : val)
            b.append(v).append(" ");
        logger.trace("Parameter: " + name + " : " + b.toString());
    }

    // ===========================================================================
    /**
     * Executes the {@link RESTMethod} specified using the given parameters and returns the
     * result.
     *
     * @param method The method to execute.
     * @param params The parameters.
     * @return The result of the method invocation.
     * @throws IOException If an error occurs.
     */
    @ContextInitializer
    public Object executeMethod(RESTMethod method, Object[] params) throws IOException
    {
        try
        {
            if (logger.isDebugEnabled()) logger.debug("Executing method: " + method.getMethod());
            Object result = method.getMethod().invoke(method.getObject(), params);
            if (logger.isDebugEnabled()) logger.debug("Method returned: " + result);
            return result;
        }
        catch (InvocationTargetException e)
        {
            Throwable c = e.getCause();
            ErrorLogger.log(getClass(), c);
            throw new IOException(c.getMessage());
        }
        catch (Throwable e)
        {
            ErrorLogger.log(getClass(), e);
            if (e instanceof IOException) throw (IOException) e;
            else throw new IOException(e.getMessage());
        }
    }

    // ===========================================================================
    @Override
    public void registered(Object t)
    {
        for (Method m : t.getClass().getMethods())
        {
            REST r = AnnotationUtility.getAnnotation(REST.class, m, t);
            if (r != null)
            {
                if (logger.isDebugEnabled()) logger.debug("Registered REST Method " + fixPath(r));
                RESTMethod rm = new RESTMethod();
                rm.setMethod(m);
                rm.setObject(t);
                rm.setForm(r.form());
                methods.put(fixPath(r), rm);
            }
        }

        if (t instanceof RESTSerializer)
        {
            RESTSerializer rs = (RESTSerializer) t;
            serializers.put(rs.getFormat(), rs);
        }

        if (t instanceof RESTAuthenticator)
            authenticators.add((RESTAuthenticator) t);

        if (t instanceof SessionManager)
            sessionManager = (SessionManager) t;
    }

    // ===========================================================================
    /**
     * Returns the path (fixed) from the annotation given.
     *
     * @param r The annotation to process.
     * @return The fixed path.
     */
    private String fixPath(REST r)
    {
        String path = r.path();
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    // ===========================================================================
    @Override
    public void unregistered(Object t)
    {
        for (Method m : t.getClass().getMethods())
        {
            REST r = AnnotationUtility.getAnnotation(REST.class, m, t);
            if (r != null) methods.remove(fixPath(r));
        }

        if (t instanceof RESTSerializer)
        {
            RESTSerializer rs = (RESTSerializer) t;
            serializers.remove(rs.getFormat());
        }

        if (t instanceof RESTAuthenticator) authenticators.remove(t);

        if (t == sessionManager) sessionManager = null;
    }

    // ===========================================================================
    /**
     * Returns the current request.
     *
     * @return The current request.
     */
    public static HttpServletRequest getCurrentRequest()
    {
        return beans.get(Thread.currentThread()).request;
    }

    // ===========================================================================
    /**
     * Returns the current response.
     *
     * @return The current response.
     */
    public static HttpServletResponse getCurrentResponse()
    {
        return beans.get(Thread.currentThread()).response;
    }
}
