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

/**
 * Defines a class that can enhance other classes.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public interface ClassEnhancer
{
    // ===========================================================================
    /**
     * Returns an enhanced version of the given class, and instantiates it.
     *
     * @param <T> The generic class;
     * @param c The class to enhance;
     * @return The type.
     * @throws Throwable If an error occurs.
     */
    <T> T getEnhanced(Class<T> c) throws Throwable;
}
