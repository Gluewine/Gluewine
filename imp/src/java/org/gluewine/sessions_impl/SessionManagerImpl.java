/**************************************************************************
 *
 * Gluewine Base Session Management Module
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
package org.gluewine.sessions_impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.gluewine.sessions.SessionExpiredException;
import org.gluewine.sessions.SessionManager;
import org.gluewine.sessions.SessionManagerDataStore;

/**
 * Simple implementation that stores sessions in a map.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class SessionManagerImpl implements SessionManager, SessionManagerDataStore
{
    // ===========================================================================
    /**
     * The map of sessions and their associated timestamps.
     */
    private Map<String, Long> sessions = new HashMap<String, Long>();

    /**
     * The map of sessions and their associated user data.
     */
    private Map<String, Map<String, Object>> sessionData = new HashMap<String, Map<String, Object>>();

    /**
     * Max amount of time in milliseconds that a session can be idle.
     */
    private static final long MAXIDLE = 300000;

    /**
     * The timer used to check the sessions.
     */
    private Timer timer = null;

    /**
     * The map of session id indexed on the thread.
     */
    private Map<Thread, String> threadSessions = new HashMap<Thread, String>();

    // ===========================================================================
    /**
     * Creates an instance.
     */
    public SessionManagerImpl()
    {
        timer = new Timer(true);
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                synchronized (sessions)
                {
                    Iterator<Entry<String, Long>> iter = sessions.entrySet().iterator();
                    while (iter.hasNext())
                    {
                        Entry<String, Long> e = iter.next();
                        if (System.currentTimeMillis() - e.getValue().longValue() > MAXIDLE)
                        {
                            sessionData.remove(e.getKey());
                            iter.remove();
                        }
                    }
                }
            }
        }, 0, MAXIDLE);
    }

    // ===========================================================================
    @Override
    public String createNewSession(String user)
    {
        synchronized (sessions)
        {
            String id = UUID.randomUUID().toString();
            sessions.put(id, Long.valueOf(System.currentTimeMillis()));
            putData(id, USERNAME, user);
            return id;
        }
    }

    // ===========================================================================
    @Override
    public void checkSession(String session)
    {
        synchronized (sessions)
        {
            boolean valid = false;
            if (sessions.containsKey(session))
                valid = System.currentTimeMillis() - sessions.get(session).longValue() < MAXIDLE;

            if (!valid) throw new SessionExpiredException();
        }
    }

    // ===========================================================================
    @Override
    public void tickSession(String session)
    {
        synchronized (sessions)
        {
            if (sessions.containsKey(session))
                sessions.put(session, Long.valueOf(System.currentTimeMillis()));
        }
    }

    // ===========================================================================
    @Override
    public void closeSession(String session)
    {
        synchronized (sessions)
        {
            sessions.remove(session);
            sessionData.remove(session);
        }
    }

    // ===========================================================================
    @Override
    public void checkAndTickSession(String session)
    {
        checkSession(session);
        tickSession(session);
    }

    // ===========================================================================
    @Override
    public void setCurrentSessionId(String session)
    {
        synchronized (threadSessions)
        {
            threadSessions.put(Thread.currentThread(), session);
        }
    }

    // ===========================================================================
    @Override
    public void clearCurrentSessionId()
    {
        synchronized (threadSessions)
        {
            threadSessions.remove(Thread.currentThread());
        }
    }

    // ===========================================================================
    @Override
    public String getCurrentSessionId()
    {
        synchronized (threadSessions)
        {
            return threadSessions.get(Thread.currentThread());
        }
    }

    // ===========================================================================
    @Override
    public void putData(String session, String key, Object data) throws SessionExpiredException
    {
        synchronized (sessionData)
        {
            checkAndTickSession(session);
            Map dataMap = sessionData.get(session);
            if (dataMap == null)
            {
                dataMap = new HashMap<String, Object>();
                sessionData.put(session, dataMap);
            }
            dataMap.put(key, data);
        }
    }

    // ===========================================================================
    @Override
    public Object getData(String session, String key) throws SessionExpiredException
    {
        synchronized (sessionData)
        {
            checkAndTickSession(session);
            Map dataMap = sessionData.get(session);
            if (dataMap == null)
                return null;
            return dataMap.get(key);
        }
    }
}
