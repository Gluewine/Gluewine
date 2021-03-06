/**************************************************************************
 *
 * Gluewine Console Module
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
package org.gluewine.console.impl;

import java.util.ArrayList;
import java.util.List;

import org.gluewine.console.CommandContext;

/**
 * Defines a table that can be outputted.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public class Table
{
    // ===========================================================================
    /**
     * The headers.
     */
    private String[] headers = null;

    /**
     * The column widths.
     */
    private int[] columnWidth = null;

    /**
     * The max width allowed for a column.
     */
    private int[] maxColumnWidth = null;

    /**
     * The rows of the table.
     */
    private List<String[]> rows = new ArrayList<String[]>();

    // ===========================================================================
    /**
     * Sets the headers of the table.
     *
     * @param s The headers.
     */
    void setHeader(String ... s)
    {
        headers = new String[s.length];
        columnWidth = new int[s.length];
        maxColumnWidth = new int[s.length];
        for (int i = 0; i < s.length; i++)
        {
            headers[i] = s[i];
            columnWidth[i] = s[i].length();
            maxColumnWidth[i] = Integer.MAX_VALUE;
        }
    }

    // ===========================================================================
    /**
     * Sets the maximum widths for the columns. 0 indicates that no restriction
     * applies.
     *
     * @param w The widths.
     */
    void setMaxWidth(int ... w)
    {
        if (maxColumnWidth == null) maxColumnWidth = new int[w.length];

        for (int i = 0; i < w.length; i++)
        {
            int max = Integer.MAX_VALUE;
            if (w[i] > 0) max = w[i];
            maxColumnWidth[i] = max;
        }
    }

    // ===========================================================================
    /**
     * Adds a row.
     *
     * @param s The row data to add.
     */
    void addRow(String ... s)
    {
        if (columnWidth == null) columnWidth = new int[s.length];
        if (maxColumnWidth == null) maxColumnWidth = new int[s.length];

        String[] row = new String[s.length];
        for (int i = 0; i < s.length; i++)
        {
            if (s[i] == null) row[i] = "";
            else row[i] = s[i];
            int l = 0;
            if (s[i] != null) l = s[i].length();
            columnWidth[i] = Math.max(columnWidth[i], l);
            if (maxColumnWidth[i] == 0) maxColumnWidth[i] = Integer.MAX_VALUE;
        }
        rows.add(row);
    }

    // ===========================================================================
    /**
     * Prints a separator line using the character specified.
     *
     * @param cc The current context.
     * @param character The separator character.
     */
    private void printSeparator(CommandContext cc, String character)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < columnWidth.length; i++)
        {
            for (int j = 0; j <= Math.min(columnWidth[i], maxColumnWidth[i]); j++)
                b.append(character);
            if (i < columnWidth.length - 1)
                b.append(character + "|" + character);
        }
        cc.println(" " + b.toString());
    }

    // ===========================================================================
    /**
     * Prints out the table to the given context.
     *
     * @param cc The context to use.
     */
    void print(CommandContext cc)
    {
        cc.println();
        StringBuilder b = new StringBuilder();
        if (headers != null)
        {
            for (int i = 0; i < headers.length; i++)
            {
                b.append(headers[i]);
                for (int j = Math.min(columnWidth[i], maxColumnWidth[i]) - headers[i].length(); j >= 0; j--)
                    b.append(" ");
                if (i < headers.length - 1)
                    b.append(" | ");
            }
            cc.println(" " + b.toString());
            b.delete(0, b.length());
            printSeparator(cc, "-");
        }

        for (String[] row : rows)
        {
            if (row[0] != null && row[0].startsWith("@@") && row[0].endsWith("@@") && row[0].length() == 5)
            {
                String fill = row[0].substring(2, 3);
                printSeparator(cc, fill);
            }
            else
            {
                for (int i = 0; i < row.length; i++)
                {
                    String s = row[i];
                    if (s == null) s = "";

                    else
                    {
                        if (s.length() > Math.min(columnWidth[i], maxColumnWidth[i]))
                            s = s.substring(0, Math.min(columnWidth[i], maxColumnWidth[i]) - 3) + "...";

                        b.append(s);
                        for (int j = Math.min(columnWidth[i], maxColumnWidth[i]) - s.length(); j >= 0; j--)
                            b.append(" ");

                        if (i < row.length - 1)
                                b.append(" | ");
                    }
                }
                cc.println(" " + b.toString());
                b.delete(0, b.length());
            }
        }
    }
}
