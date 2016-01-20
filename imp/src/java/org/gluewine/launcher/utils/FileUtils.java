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
package org.gluewine.launcher.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that offers some SHA1 computations.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public final class FileUtils
{
    // ===========================================================================
    /**
     * Use the static methods to access the class.
     */
    private FileUtils()
    {
    }

    // ===========================================================================
    /**
     * Computes the SHA1 hashcode of the file.
     *
     * @param input The String to process.
     * @return The hashcode.
     * @throws NoSuchAlgorithmException If the SHA1 algorithm is not defined.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public static String getSHA1HashCode(String input) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] dataBytes = input.getBytes();
        md.update(dataBytes, 0, dataBytes.length);
        return hashCodeToString(md.digest());
    }

    // ===========================================================================
    /**
     * Computes the SHA1 hashcode of the file.
     *
     * @param f The file to process.
     * @return The hashcode.
     * @throws IOException If a problem occurs opening the file.
     */
    public static String getSHA1HashCode(File f) throws IOException
    {
        return getSHA1HashCode(f.toURI().toURL());
    }

    // ===========================================================================
    /**
     * Computes the SHA1 hashcode of the file.
     *
     * @param url The url to process.
     * @return The hashcode.
     * @throws IOException If a problem occurs opening the file.
     */
    public static String getSHA1HashCode(URL url) throws IOException
    {
        InputStream is = null;
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            is = url.openStream();
            byte[] dataBytes = new byte[1024];

            int nread = 0;

            while ((nread = is.read(dataBytes)) != -1)
                md.update(dataBytes, 0, nread);

            return hashCodeToString(md.digest());
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            throw new IOException(e.getMessage());
        }
        finally
        {
            if (is != null)
                is.close();
        }
    }

    // ===========================================================================
    /**
     * Converts the given bytes to a hex String.
     *
     * @param hash The bytes.
     * @return The String.
     */
    private static String hashCodeToString(byte[] hash)
    {
        StringBuffer sb = new StringBuffer("");
        for (int i = 0; i < hash.length; i++)
            sb.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1));

        return sb.toString();
    }

    // ===========================================================================
    /**
     * Reads the file specified and returns its content as a list of Strings.
     * Empty lines and lines starting with # are removed.
     *
     * @param file The file to process.
     * @return The content.
     * @throws IOException Thrown if a read error occurs.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public static List<String> readFile(File file) throws IOException
    {
        BufferedReader reader = null;
        List<String> content = new ArrayList<String>();
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                if (!line.startsWith("#"))
                    content.add(line);
            }
        }
        finally
        {
            if (reader != null) reader.close();
        }

        return content;
    }
}
