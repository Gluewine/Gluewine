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
package org.gluewine.core.glue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.gluewine.core.AspectProvider;
import org.gluewine.core.ContextInitializer;
import org.gluewine.core.InterceptChainStartOnly;
import org.gluewine.core.RepositoryListener;

/**
 * Default interceptor. Specific enhancers (cfr. CGLIBEnhancer) should subclass this
 * class and add their specific methods to it.
 *
 * <p>The general contract for interceptors is to respect the following flow:
 * <ul>
 * <li>registerFirstInChain() - To obtain the firstInChain flag, required for some of the next calls.</li>
 * <li>invokeBefore() - This will invoke the invokeBefore method on the registered providers and update the stack of 'active' providers.</li>
 * <li>invoke the method on the object - This is implementation dependent.</li>
 * <li>invoke the afterSuccess of afterFailure depending on whether previous method call succeeded or failed.</li>
 * <li>clearThread(firstInChain) - To cleanup up</li>
 * </ul>
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class Interceptor implements RepositoryListener<AspectProvider>
{
    // ===========================================================================
    /** List p of registerd providers. */
    private List<AspectProvider> providers = new ArrayList<AspectProvider>();

    /** List of providers that will only be invoked at the start of a command chain. */
    private Set<AspectProvider> chainStartProviders = new HashSet<AspectProvider>();

    /** List of providers that are invoked to initize the stack context. */
    private Set<AspectProvider> contextProviders = new HashSet<AspectProvider>();

    /**
     * Set containing the thread chains.
     */
    private Set<Thread> chainThreads = new HashSet<Thread>();

    /**
     * The logger instance.
     */
    private Logger logger = Logger.getLogger(getClass());

    // ===========================================================================
    /**
     * Creates an instance.
     */
    Interceptor()
    {
    }

    // ===========================================================================
    /**
     * Checks if this call is the first one in the stack of this thread. If so,
     * true is returned and the thread is registered if the register flag is set.
     *
     * @param register If true the current thread is registered at the end of the call.
     * @return True if this method call is the first one in the chain.
     */
    public boolean registerFirstInChain(boolean register)
    {
        boolean firstInChain = !chainThreads.contains(Thread.currentThread());
        if (firstInChain && register) chainThreads.add(Thread.currentThread());
        return firstInChain;
    }

    // ===========================================================================
    /**
     * Clears the thread from the chainThreads.
     *
     * @param firstInChain True if first in chain.
     */
    public void clearThread(boolean firstInChain)
    {
        if (firstInChain)
            chainThreads.remove(Thread.currentThread());
    }

    // ===========================================================================
    /**
     * Invokes the invokeBefore method on the providers. All providers that are
     * successfully invoked will be pushed in the given stack.
     *
     * @param stack The stack to update.
     * @param o The object that is being processed.
     * @param m The method that is being executed.
     * @param params The method parameters.
     * @param firstInChain True if this method is the first in the stacktrace for the current thread.
     * @param initializeContext If true only the providers registered as ContextInitializers will be used.
     */
    public void invokeBefore(Stack<AspectProvider> stack, Object o, Method m, Object[] params, boolean firstInChain, boolean initializeContext)
    {
        try
        {
            List<AspectProvider> tempProviders = new ArrayList<AspectProvider>();
            if (initializeContext) tempProviders.addAll(contextProviders);
            else
            {
                if (firstInChain) tempProviders.addAll(chainStartProviders);
                tempProviders.addAll(providers);
            }

            for (AspectProvider p : tempProviders)
            {
                p.beforeInvocation(o, m, params);
                stack.push(p);
            }
        }
        catch (Throwable e)
        {
            logger.error("An error occurred invoking an beforeInvocation method: " + e.getMessage());
            invokeAfterFailure(stack, stack, m, params, e);

            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new RuntimeException(e);
        }
    }

    // ===========================================================================
    /**
     * Invokes the invokeAfterSuccess method on the providers in the given stack.
     *
     * @param stack The providers to use.
     * @param o The object that is being processed.
     * @param m The method that is being executed.
     * @param params The method parameters.
     * @param result The result of the method execution. (null if the method is void)
     */
    public void invokeAfterSuccess(Stack<AspectProvider> stack, Object o, Method m, Object[] params, Object result)
    {
        try
        {
            while (!stack.isEmpty())
            {
                AspectProvider p = stack.pop();
                p.afterSuccess(o, m, params, result);
                p.after(o, m, params);
            }
        }
        catch (RuntimeException e)
        {
            logger.error("An error occurred invoking an afterSuccess or after method: " + e.getMessage());
            invokeAfterFailure(stack, o, m, params, e);
            throw e;
        }
    }

    // ===========================================================================
    /**
     * Invokes the invokeAfterFailure method on the providers in the given stack.
     *
     * @param stack The providers to use.
     * @param o The object that is being processed.
     * @param m The method that is being executed.
     * @param params The method parameters.
     * @param e The exception thrown by the method.
     */
    public void invokeAfterFailure(Stack<AspectProvider> stack, Object o, Method m, Object[] params, Throwable e)
    {
        while (!stack.isEmpty())
        {
            try
            {
                AspectProvider p = stack.pop();
                p.afterFailure(o, m, params, e);
                p.after(o, m, params);
            }
            catch (Throwable t)
            {
                logger.error("An error occurred invoking an afterFailure or after method: " + t.getMessage());
            }
        }
    }

    // ===========================================================================
    @Override
    public void registered(AspectProvider provider)
    {
        if (!providers.contains(provider))
        {
            logger.debug("Registering AspectProvider " + provider.getClass().getName());
            if (provider.getClass().getAnnotation(ContextInitializer.class) != null)
                contextProviders.add(provider);

            if (provider.getClass().getAnnotation(InterceptChainStartOnly.class) != null)
                chainStartProviders.add(provider);
            else
                providers.add(provider);
        }
    }

    // ===========================================================================
    @Override
    public void unregistered(AspectProvider provider)
    {
        chainStartProviders.remove(provider);
        providers.remove(provider);
    }
}
