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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import jline.console.history.FileHistory;

import org.gluewine.authentication.AuthenticationAbortedException;
import org.gluewine.console.AnsiCodes;
import org.gluewine.console.CLICommand;
import org.gluewine.console.CLIOption;
import org.gluewine.console.ConsoleServer;
import org.gluewine.console.FailedResponse;
import org.gluewine.console.Request;
import org.gluewine.console.Response;
import org.gluewine.console.SyntaxException;
import org.gluewine.console.SyntaxResponse;
import org.gluewine.gxo_client.GxoClient;

/**
 * Starts the console client.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public final class ConsoleClient implements Runnable, Completer, AnsiCodes
{
    // ===========================================================================
    /**
     * The GXO Client instance to use.
     */
    private GxoClient client = null;

    /**
     * The prompt to use.
     */
    private String prompt = null;

    /**
     * The server connected to.
     */
    private ConsoleServer server = null;

    /**
     * The console reader.
     */
    private ConsoleReader reader = null;

    /**
     * Flag indicating that a stop has been requested.
     */
    private boolean stopRequested = false;

    /**
     * Flag indicating that the output is routed.
     */
    private boolean outputRouted = false;

    /**
     * Name of the output file when output is routed.
     */
    private String outputFile = null;

    /**
     * The writer used for output.
     */
    private BufferedWriter writer = null;

    /**
     * Flag indicating that the processing has just been initialized.
     */
    private boolean initial = true;

    /**
     * The welcome string.
     */
    private static final String WELCOME = "\u001b[47m\u001b[30m                                          \n"
                                          + "            \u001b[31mGluewine Framework\u001b[30m            \n"
                                          + "                                          \n"
                                          + "             www.gluewine.org             \n"
                                          + "         Apache version 2 license         \n"
                                          + "                                          \n"
                                          + "              \u001b[32m(c) FKS bvba\u001b[30m                \n"
                                          + "                                          \n\u001b[0m";

    // ===========================================================================
    /**
     * Creates an instance.
     *
     * @param host The host running the ConsoleServer.
     * @param port The port.
     */
    private ConsoleClient(String host, int port)
    {
        client = new GxoClient(host, port);
        prompt = host + "> ";
    }

    // ===========================================================================
    /**
     * Executes the list of commands.
     *
     * @param dirContext The current directory context. May be null.
     * @param batch Indicates whether the commands are comming from a batch file.
     * @param interactive Indicates that the command options were entered interactively.
     * @param cmds The commands to execute.
     */
    private void executeCommands(File dirContext, boolean batch, boolean interactive, String ... cmds)
    {
        for (int i = 0; i < cmds.length; i++)
        {
            try
            {
                String cmd = cmds[i];

                if (cmd.startsWith("exit") || cmd.startsWith("close"))
                {
                    stopRequested = true;
                    return;
                }

                else if (cmd.equals("cls") || cmd.equals("clear"))
                {
                    reader.println(CLS);
                    reader.print(HOME);
                    for (int l = 0; l < 8; l++)
                        reader.println("                                                                                                      ");
                    reader.println(CLS + HOME);
                }

                else if (cmd.startsWith(">"))
                {
                    outputFile = cmd.substring(1).trim();
                    if (outputFile.equals("!"))
                    {
                        if (writer != null)
                        {
                            outputRouted = false;
                            writer.close();
                            writer = null;
                        }
                    }
                    else
                    {
                        outputRouted = true;
                        writer = prepareOutputFile(outputFile);
                    }
                }

                else if (cmd.startsWith("logoff"))
                {
                    server = null;
                    initial = true;
                    prompt = "local>";
                    return;
                }

                else if (cmd.startsWith("exec"))
                {
                    File f = null;
                    if (dirContext != null) f = new File(dirContext, cmd.substring(4).trim());
                    else f = new File(cmd.substring(4).trim());

                    executeCommands(f.getParentFile(), true, false, loadExecutionFile(f));
                }

                else
                {
                    Request req = new Request();
                    req.setBatch(batch);
                    req.setCommand(cmd);
                    req.setOutputRouted(outputRouted);
                    Response output = server.executeCommand(req);
                    if (output instanceof FailedResponse)
                    {
                        FailedResponse r = (FailedResponse) output;
                        StringWriter w = new StringWriter();
                        PrintWriter pw = new PrintWriter(w);
                        r.getException().printStackTrace(pw);
                        printOut("\u001b[31;1m" + w.getBuffer().toString() + "\u001b[0m");
                    }
                    else if (output instanceof SyntaxResponse)
                    {
                        SyntaxResponse sr = (SyntaxResponse) output;
                        printOut("\u001b[31;1m" + sr.getOuput() + "\u001b[0m");
                        if (!sr.isInteractive() && !sr.isBatch() && !sr.isOutputRouted())
                            askCommandOptionsInteractively(sr.getCommand());
                    }
                    else printOut(output.getOuput());
                }
            }
            catch (ConnectException e)
            {
                System.out.println("Connection lost!");
                prompt = "local>";
                server = null;
                return;
            }
            catch (SyntaxException e)
            {
                System.out.println("\u001b[31;1m" + e.getMessage() + "\u001b[0m");
            }
            catch (Throwable e)
            {
                if (e.getMessage() != null && e.getMessage().startsWith("org.gluewine.sessions.SessionExpiredException:"))
                {
                    System.out.println("Session Expired!");
                    initial = true;
                    server = null;
                    return;
                }
                else
                {
                    System.out.println("An error occured on line: " + (i + 1));
                    e.printStackTrace();
                }
            }
        }
    }

    // ===========================================================================
    /**
     * Asks for the command options interactively.
     *
     * @param command The command to process.
     * @throws IOException Thrown if an error occurs reading from the screen.
     */
    private void askCommandOptionsInteractively(CLICommand command) throws IOException
    {
        List<CLIOption> options = new ArrayList<CLIOption>();
        for (CLIOption opt : command.getOptions())
        {
            if (opt.isRequired()) options.add(0, opt);
            else options.add(opt);
        }

        StringBuffer b = new StringBuffer(command.getName()).append(" ");
        for (int i = 0; i < options.size(); i++)
        {
            askOption(b, options.get(i));
            if (i < options.size() - 1)
                b.append(" ");
        }

        executeCommands(null, false, true, b.toString());
    }

    // ===========================================================================
    /**
     * Asks for the given option, and updates the stringbuffer with the value entered.
     *
     * @param b The buffer to update.
     * @param opt The option to process.
     * @throws IOException Thrown if an error occurs reading from the screen.
     */
    private void askOption(StringBuffer b, CLIOption opt) throws IOException
    {
        if (opt.isRequired())
        {
            if (opt.needsValue())
            {
                String val = reader.readLine("\u001b[35;1m " + opt.getName() + " (" + opt.getDescription() + "): \u001b[0m", opt.getMask());
                b.append(" " + opt.getName()).append(" ").append(val);
            }
            else b.append(opt.getName());
        }
        else
        {
            if (opt.needsValue())
            {
                String val = reader.readLine("\u001b[33;1m [" + opt.getName() + " (" + opt.getDescription() + ")]: \u001b[0m", opt.getMask());
                if (val.trim().length() > 0) b.append(opt.getName()).append(" ").append(val);
            }
            else
            {
                String val = reader.readLine("\u001b[33;1m [ Use " + opt.getName() + " (" + opt.getDescription() + ") Y|N ] \u001b[0m");
                if ("y".equalsIgnoreCase(val.trim())) b.append(opt.getName());
            }
        }
    }

    // ===========================================================================
    /**
     * Outputs the given string.
     *
     * @param s The string to output.
     * @throws Throwable If an error occurs.
     */
    private void printOut(String s) throws Throwable
    {
        if (outputRouted)
        {
            writer.write(s);
            writer.newLine();
            writer.flush();
        }
        else reader.println(s);
    }

    // ===========================================================================
    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_ALWAYS_NULL")
    public void run()
    {
        try
        {
            reader = new ConsoleReader();
            reader.clearScreen();
            reader.setBellEnabled(true);
            reader.println(WELCOME);

            String home = System.getProperty("user.home");
            FileHistory his = new FileHistory(new File(home, ".osgi_history"));
            reader.setHistory(his);
            reader.setHistoryEnabled(true);
            reader.addCompleter(this);

            while (!stopRequested)
            {
                try
                {
                    if (server == null && initial)
                    {
                        server = client.getService(ConsoleServer.class, "-1", getIpAddress());
                        if (server.needsAuthentication())
                            authenticate(reader);
                        initial = false;
                    }

                    String line = reader.readLine(prompt);
                    if (line == null)
                    {
                        System.out.println("Input closed. Closing console.");
                        stopRequested = true;
                    }
                    else
                    {
                        executeCommands(null, false, false, line);
                    }
                }
                catch (AuthenticationAbortedException e)
                {
                    System.out.println("Authentication Aborted! Closing console.");
                    stopRequested = true;
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                    System.out.println("Cannot connect to server!");
                }
            }
            his.flush();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    // ===========================================================================
    /**
     * Loads the commands specified in the given file.
     *
     * @param file The file to load.
     * @return The list of commands.
     *
     * @throws IOException if the file cannot be opened.
     */
    private String[] loadExecutionFile(File file) throws IOException
    {
        List<String> cmds = new ArrayList<String>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")))
        {
            String line;
            while ((line = in.readLine()) != null)
            {
                line = line.trim();
                if (line.length() > 0) cmds.add(line);
            }
        }

        return cmds.toArray(new String[cmds.size()]);
    }

    // ===========================================================================
    /**
     * Prepares the output file.
     *
     * @param file The file.
     * @return The printwriter to use.
     * @throws IOException If an error occurs.
     */
    private BufferedWriter prepareOutputFile(String file) throws IOException
    {
        File f = new File(file);
        if (!f.getParentFile().exists())
            if (!f.getParentFile().mkdirs())
                throw new IOException("Could not create dir: " + f.getParentFile().getAbsolutePath());

        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
    }

    // ===========================================================================
    /**
     * Performs authentication.
     * @throws Throwable If the authentication class could not be found or could not
     * be instantiated.
     *
     * @param reader The current reader to use.
     */
    private void authenticate(ConsoleReader reader) throws Throwable
    {
        String clazz = getAuthenticatorClass(reader);

        Class<?> srvdef = getClass().getClassLoader().loadClass(clazz);
        Class<?> cl = getClass().getClassLoader().loadClass(clazz + "TxtClient");
        Object o = cl.newInstance();
        Class<?>[] parameterTypes = new Class<?>[] {srvdef, ConsoleReader.class};
        Method m = cl.getMethod("authenticate", parameterTypes);

        Object srv = client.getService(srvdef, "-1", getIpAddress());
        Object[] params = new Object[] {srv, reader};
        Object id = m.invoke(o, params);
        if (id instanceof String)
            server = client.getService(ConsoleServer.class, (String) id, getIpAddress());

        String msg = server.getWelcomeMessage();
        if (msg != null) System.out.println(msg);

        String p = server.getPrompt();
        if (p != null && p.length() > 0) prompt = p;
    }

    // ===========================================================================
    /**
     * Returns the authenticator class to use. If there's only one available it will
     * be used. If there are more available the user will be prompted to select one.
     *
     * @param reader The reader to use.
     * @return The clazz choosen by the user.
     * @throws Throwable If an error occurs.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_ALWAYS_NULL")
    private String getAuthenticatorClass(ConsoleReader reader) throws Throwable
    {
        Map<String, String> auths = server.getAvailableAuthenticators();
        Map<String, String> sorted = new TreeMap<String, String>();
        sorted.putAll(auths);
        List<String> indexed = new ArrayList<String>(sorted.size());
        indexed.addAll(sorted.keySet());
        sorted.putAll(auths);
        String clazz = null;

        if (indexed.size() == 1) clazz = sorted.get(indexed.get(0));

        while (clazz == null)
        {
            for (int i = 0; i < indexed.size(); i++)
                System.out.println((i + 1) + "\t" + indexed.get(i));

            String l = reader.readLine("Select an authentication method: ");
            try
            {
                int index = Integer.parseInt(l) - 1;
                if (index >= 0 && index < indexed.size())
                    clazz = sorted.get(indexed.get(index));
            }
            catch (NumberFormatException e)
            {
                System.out.println("Enter a value between 1 and " + indexed.size());
            }
        }

        return clazz;
    }

    // ===========================================================================
    /**
     * Gets the ip address.
     *
     * @return The ip address.
     */
    private String getIpAddress()
    {
        try
        {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements())
            {
                NetworkInterface inf = en.nextElement();
                if (inf.isUp() && !inf.isVirtual() && !inf.isLoopback())
                {
                    Enumeration<InetAddress> addrs = inf.getInetAddresses();
                    while (addrs.hasMoreElements())
                    {
                        InetAddress addr = addrs.nextElement();
                        return addr.getHostAddress();
                    }
                }
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }

        return null;
    }

    // ===========================================================================
    @Override
    @SuppressWarnings({"rawtypes", "unchecked" })
    public int complete(String s, int i, List l)
    {
        if (s == null) s = "";
        List<String> r = server.complete(s);
        l.clear();
        l.addAll(r);

        int cursor = 0;
        int b = s.lastIndexOf(' ');
        if (b > -1)
            cursor = b + 1;

        int sep = s.lastIndexOf(File.separator);
        if (sep > -1)
            cursor = sep + 1;

        return cursor;
    }

    // ===========================================================================
    /**
     * Main invocation routine.
     *
     * @param args The CLI arguments.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_ALWAYS_NULL")
    public static void main(String[] args)
    {
        if (args == null || args.length < 2)
        {
            System.out.println("Syntax: ConsoleClient <host> <port>");
            System.exit(8);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        ConsoleClient client = new ConsoleClient(host, port);
        Thread th = new Thread(client);
        th.setDaemon(false);
        th.start();
    }
}
