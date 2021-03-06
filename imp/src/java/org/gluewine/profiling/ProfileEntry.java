/**************************************************************************
 *
 * Gluewine Profiler Integration Module
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
package org.gluewine.profiling;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Defines a profil entry.
 *
 * @author fks/Serge de Schaetzen
 *
 */
@Entity
@Table(name = "gluewine_profiling")
public class ProfileEntry
{
    // ===========================================================================
    /**
     * The classname.
     */
    @Column(name = "class_name")
    private String className = null;

    /**
     * The amount of time (in milliseconds) of the execution.
     */
    private long duration;

    /**
     * The type of exception. (if any)
     */
    private String exception;

    /**
     * The timestamp of the execution.
     */
    @Column(name = "execution_date")
    private long executionDate;

    /**
     * The id.
     */
    @Id
    @GeneratedValue
    private long id;

    /**
     * The method being profiled.
     */
    private String method = null;

    /**
     * Flag indicating whether the method was executed successfully.
     */
    private boolean success = false;

    // ===========================================================================
    /**
     * Creates an instance.
     */
    public ProfileEntry()
    {
    }

    // ===========================================================================
    /**
     * Creates an entry with the given data.
     *
     * @param clazz The class name.
     * @param method The method name.
     * @param time The time of execution.
     */
    ProfileEntry(String clazz, String method, long time)
    {
        this.className = clazz;
        this.method = method;
        this.executionDate = time;
    }

    // ===========================================================================
    /**
     * Returns the class name.
     *
     * @return The class name.
     */
    public String getClassName()
    {
        return className;
    }

    // ===========================================================================
    /**
     * Returns the execution duration.
     *
     * @return The execution duration.
     */
    public long getDuration()
    {
        return duration;
    }

    // ===========================================================================
    /**
     * Returns the exception type.
     *
     * @return The exception type.
     */
    public String getException()
    {
        return exception;
    }

    // ===========================================================================
    /**
     * Returns the execution date.
     *
     * @return The date.
     */
    public long getExecutionDate()
    {
        return executionDate;
    }

    // ===========================================================================
    /**
     * Returns the id.
     *
     * @return The id.
     */
    public long getId()
    {
        return id;
    }

    // ===========================================================================
    /**
     * Returns the method name
     * .
     * @return The method name.
     */
    public String getMethod()
    {
        return method;
    }

    // ===========================================================================
    /**
     * Returns true if the execution was successfull.
     *
     * @return True if successfull.
     */
    public boolean isSuccessfull()
    {
        return success;
    }

    // ===========================================================================
    /**
     * Sets the duration.
     *
     * @param duration The duration.
     */
    public void setDuraction(long duration)
    {
        this.duration = duration;
    }

    // ===========================================================================
    /**
     * Sets the success flag.
     *
     * @param success The success flag.
     */
    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    // ===========================================================================
    /**
     * Sets the exception.
     *
     * @param exception The exception.
     */
    public void setException(String exception)
    {
        this.exception = exception;
    }
}
