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
package org.gluewine.sessions;

/**
 * Allows storing data with sessions.
 *
 * @author fks/Frank Gevaerts
 */

public interface SessionManagerDataStore
{

    /**
     * The key used to store usernames.
     */
    String USERNAME = "USERNAME";

    /**
     * Stores named data with a session.
     *
     * @param session the session id.
     * @param key the key to store data with.
     * @param data the data to store.
     * @throws SessionExpiredException when the session has expired.
     */
    void putData(String session, String key, Object data) throws SessionExpiredException;

    /**
     * Stores named data with a session.
     *
     * @param session the session id.
     * @param key the key to store data with.
     * @throws SessionExpiredException when the session has expired.
     * @return the data that was stored, or null.
     */
    Object getData(String session, String key) throws SessionExpiredException;
}

