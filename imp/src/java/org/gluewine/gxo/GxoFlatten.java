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
package org.gluewine.gxo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Used from an ANT Task. It will read a configuration file 'gxo_shared.lst'
 * and copy all classes specified in the file to the target root.
 *
 * The package name of the classes is changed.
 *
 * The application requires 2 parameters:
 *
 * - the source directory containing the files specified in the 'gxo_shared.lst'
 * - the target directory where the modified files are copied to. This directory
 *   is suffixed with 'src/'.
 *
 * @author fks/Serge de Schaetzen
 */
public final class GxoFlatten
{
    // ===========================================================================
    /**
     * The name of the file containing the shared file names.
     */
    private static final String GXO_FILE = "gxo_shared.lst";

    /**
     * File containing names of classes that are imported in GWT.
     */
    private static final String GXO_IMP = "gxo_imported.lst";


    /**
     * File containing names of classes that need to be renamed in GWT.
     */
    private static final String GXO_REP = "gxo_replace.lst";

    // ===========================================================================
    /**
     * Access it through the main method.
     */
    private GxoFlatten()
    {
    }

    // ===========================================================================
    /**
     * Application entry point.
     *
     * @param args The CLI arguments.
     */
    public static void main(String[] args)
    {
        try
        {
            String srcRoot = args[0];
            String tgtRoot = args[1];

            File source = new File(srcRoot);
            File target = new File(tgtRoot);

            Map<String, String> mappings = new HashMap<String, String>();

            List<String> files = readFile(new File(GXO_IMP));
            Set<String> allowed = new HashSet<String>();
            for (String s : files)
            {
                allowed.add("import " + s + ";");
            }

            files = readFile(new File(GXO_REP));
            Map<String, String> replace = new HashMap<String, String>();
            for (String s : files)
            {
                String[] split = s.split("=");
                replace.put("import " + split[0] + ";", "import " + split[1] + ";");
            }

            files = readFile(new File(GXO_FILE));
            for (String s : files)
            {
                int i = s.lastIndexOf('.');
                String sub = s.substring(0, i).replace('/', '.');
                allowed.add("import " + sub + ";");
            }

            for (String s : files)
            {
                File f = new File(srcRoot, s);
                File t = new File(tgtRoot, s);

                mappings.put(getClassNameFromFile(t, target), getClassNameFromFile(f, source));

                if (f.lastModified() > t.lastModified())
                {
                    List<String> sc = readFile(f);
                    List<String> tc = new ArrayList<String>();
                    for (String l : sc)
                    {
                        String ll = l.trim();
                        if (ll.startsWith("import"))
                        {
                            if (l.indexOf("java.util") >= 0 || l.indexOf("java.lang") >= 0 || l.indexOf("java.io.Serializable") >= 0 || allowed.contains(ll))
                                tc.add(l);

                            else if (replace.containsKey(ll))
                                tc.add(replace.get(ll));
                        }

                        else if (ll.startsWith("@"))
                        {
                            // Remove annotations.
                        }

                        else if (ll.trim().startsWith("public class") || ll.trim().startsWith("public abstract class"))
                        {
                            if (ll.indexOf("Serializable") < 0)
                            {
                                StringBuilder b = new StringBuilder(ll);
                                if (ll.indexOf("implements") < 0)
                                    b.append(" implements ");
                                else
                                    b.append(", ");

                                b.append("java.io.Serializable");
                                ll = b.toString();
                            }
                            tc.add(ll);
                        }

                        else
                            tc.add(l);
                    }


                    System.out.println("Copying : " + f.getName());
                    writeFile(t, tc);
                }
            }

            List<String> map = new ArrayList<String>();
            for (Entry<String, String> e : mappings.entrySet())
                map.add(e.getKey() + "=" + e.getValue());

            //writeFile(new File("../../rtf/cfg/generic/gxo_mapping.properties"), map);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            System.exit(8);
        }
    }

    // ===========================================================================
    /**
     * Returns the class name from a file residing in a given source.
     * (the source is stripped off, the .java extension is thrown away an all
     * path delimiters are replaced by .).
     *
     * @param f The file to process.
     * @param src The source the file is residing in.
     * @return The classname.
     */
    private static String getClassNameFromFile(File f, File src)
    {
        String s = src.getAbsolutePath();
        String c = f.getAbsolutePath();
        c = c.substring(s.length() + 1);
        int i = c.indexOf(".java");
        if (i > 0)
            c = c.substring(0, i);

        c = c.replace('/', '.').replace('\\', '.');
        return c;
    }

    // ===========================================================================
    /**
     * Writes the content of the list to the file specified, overriding whatever
     * was in the file.
     *
     * @param f The file to process.
     * @param c The content to write.
     * @throws Throwable If an error occurs accessing the file.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    private static void writeFile(File f, List<String> c) throws Throwable
    {
        if (!f.exists())
            if (!f.getParentFile().exists())
                if (!f.getParentFile().mkdirs())
                    throw new IOException("Could not create directory " + f.getParentFile().getAbsolutePath());

        BufferedWriter out = null;
        try
        {
            out = new BufferedWriter(new FileWriter(f));
            for (String s : c)
            {
                out.write(s);
                out.newLine();
            }
        }
        finally
        {
            if (out != null) out.close();
        }
    }

    // ===========================================================================
    /**
     * Reads a (text) file, and returns the content as a list of Strings.
     *
     * @param f The file to read.
     * @return The content of the file.
     * @throws Throwable If a problem occurs.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    private static List<String> readFile(File f) throws Throwable
    {
        List<String> files = new ArrayList<String>();

        if (f.exists())
        {
            BufferedReader in = null;
            try
            {
                in = new BufferedReader(new FileReader(f));
                String s;
                while ((s = in.readLine()) != null)
                {
                    if (!s.trim().startsWith("#") && s.trim().length() > 0)
                        files.add(s);
                }
            }
            finally
            {
                if (in != null) in.close();
            }
        }

        return files;
    }
}
