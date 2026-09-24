/**
 * @file        BridgeScriptRunner.java
 * @brief       Reads and executes robot script files.
 */
package com.fbcti.sanbot.bridge.app;

import com.fbcti.sanbot.bridge.transport.BridgeRequest;
import com.fbcti.sanbot.bridge.transport.HttpStatus;
import com.fbcti.sanbot.bridge.transport.JsonResponse;
import com.fbcti.sanbot.bridge.util.BridgeLog;
import com.fbcti.sanbot.bridge.util.FileUtils;
import com.fbcti.sanbot.bridge.util.JsonUtils;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Script executor.
 *
 * This class manages and executes scripts containing instruction that are handles sequentially.
 * The complete script is parsed before any requests are forwarded to the service. The following
 * instructions are currently supported:
 * -e startloop - Defines the start of a block that is repeated a specific number of times.
 * -e endloop - Defines the end of a block that is repeated.
 * -e pause - Pauses the specified number of seconds.
 * -e execute - Executes a robot command.
 * 
 * @todo    06/09/2026 - Allllow first line of script file to specify script runtime parameters, 
 *
 * @version     1.0.001
 * @date        7 sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public class BridgeScriptRunner extends Thread
{
    /** Source label used for log messages. */
    private static final String TAG = "BridgeScriptRunner";

    /** Callback host to which to forward bridge requests. */
    private final RequestHost requestHost;

    /** Instructions to execute. */
    private final List<Instruction> instructions = new ArrayList<>();

    /** Flag specifying if script must be terminated if an error occurs. */
    private final boolean stopOnError = true;

    /** Flag that specifies execution of the script is cancelled. */
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Constructs a new BridgeScriptRunner instance.
     *
     * The specified script file is parsed, and the callback host is copied to a member variable if
     * valid.
     *
     * @param   scriptFile      script file to execute
     * @param   requestHost     callback host to which to forward command requests
     *
     * @throws  InterruptedException thrown if script parsing is interrupted
     * @throws  IOException     thrown if the script file could not be read
     */
    BridgeScriptRunner(File scriptFile, RequestHost requestHost) throws InterruptedException, IOException
    {
        super("BridgeScriptRunner");

        // Parse script file.
        parseScript(scriptFile);

        // Copy callback host
        if (requestHost == null) throw new IllegalArgumentException("Request host is required");
        this.requestHost = requestHost;
    }

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Executes the instructions.
     */
    @Override
    public void run()
    {
        int line;
        Deque<Loop> loops = new ArrayDeque<>();
        int position = 0;
        while ((position < instructions.size()) && (cancelled.get() == false) && (isInterrupted() == false))
        {
            Instruction instruction = instructions.get(position);
            line = instruction.line;
            switch (instruction.keyword)
            {
                case "startloop":
                    if (instruction.value == 0) position = instruction.match;
                    else loops.push(new Loop(position, instruction.value));
                    break;
                case "endloop":
                    Loop loop = loops.peek();
                    if ((loop != null) && (--loop.remaining > 0)) position = loop.start;
                    else loops.pop();
                    break;
                case "pause":
                    try { Thread.sleep(instruction.value); }
                    catch (InterruptedException ignored) {}
                    break;
                case "execute":
                    BridgeRequest request = parseRequest(instruction.json, line);
                    JsonResponse response = requestHost.execute(request);
                    if ((response == null) || (response.code != HttpStatus.OK.code))
                    {
                        BridgeLog.error(TAG, "Instruction " + request.toString() +  " on line " + line + " failed"
                            + ((response == null) ? "" : " with error + response.code + "));
                        if (stopOnError) return;
                    }
                    break;
                default:
                    BridgeLog.error(TAG, "Unknown instruction on line " + line);
            }
            position++;
        }
    }

    /**
     * Stops the execution of the script.
     *
     * Pending pauses are cancelled immediately.
     */
    public void kill()
    {
        cancelled.set(true);
        interrupt();
    }

    /***********************************************************************************************
     * PRIVATE METHODS
     **********************************************************************************************/

    /**
     * Reads and parses instructions from script file
     *
     * @param   scriptFile      Java @c File instance representing script file
     */
    private void parseScript(File scriptFile) throws InterruptedException, IOException
    {
        Deque<Integer> loops = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(FileUtils.readTextFile(scriptFile))))
        {
            String text;
            int line = 0;
            while ((text = reader.readLine()) != null)
            {
                if ((cancelled.get()) || (isInterrupted())) throw new InterruptedException();
                line++;
                if ((line == 1) && (text.startsWith("\uFEFF"))) text = text.substring(1);

                // Skip empty line.
                text = text.trim();
                if (text.isEmpty()) continue;

                // Get keyword and arguments.
                String[] parts = text.split("\\s+", 2);
                String keyword = parts[0].toLowerCase(Locale.ROOT);
                String argument = (parts.length == 2) ? parts[1].trim() : "";

                // Create new instruction.
                Instruction instruction = new Instruction(line, keyword);
                switch (keyword)
                {
                    case "startloop":
                        try
                        {
                            instruction.value = Long.parseLong(argument);
                            if (instruction.value < 0) throw new NumberFormatException();
                        }
                        catch (NumberFormatException e)
                        {
                            throw error(line, "startloop requires a non-negative integer");
                        }
                        loops.push(instructions.size());
                        break;
                    case "endloop":
                        if ((argument.isEmpty() == false) || (loops.isEmpty()))
                            throw error(line, "Unexpected endloop or extra arguments");
                        instructions.get(loops.pop()).match = instructions.size();
                        break;
                    case "pause":
                        try
                        {
                            BigDecimal seconds = new BigDecimal(argument);
                            if (seconds.signum() < 0) throw new ArithmeticException();
                            instruction.value = seconds.multiply(BigDecimal.valueOf(1000)).longValueExact();
                        }
                        catch (NumberFormatException | ArithmeticException e)
                        {
                            throw error(line, "pause requires non-negative seconds with millisecond precision");
                        }
                        break;
                    case "execute":
                        StringBuilder json = new StringBuilder();
                        int depth = 0;
                        boolean quoted = false;
                        boolean escaped = false;
                        boolean started = false;
                        boolean complete = false;
                        do
                        {
                            for (int i = 0; i < argument.length(); i++)
                            {
                                char c = argument.charAt(i);
                                if ((complete) && (Character.isWhitespace(c) == false))
                                    throw error(instruction.line, "Unexpected text after JSON object");
                                if (started == false)
                                {
                                    if (Character.isWhitespace(c)) continue;
                                    if (c != '{') throw error(instruction.line, "execute requires a JSON object");
                                    started = true;
                                }
                                if (quoted)
                                {
                                    if (escaped) escaped = false;
                                    else if (c == '\\') escaped = true;
                                    else if (c == '"') quoted = false;
                                }
                                else if (c == '"') quoted = true;
                                else if ((c == '{') || (c == '[')) depth++;
                                else if ((c == '}') || (c == ']'))
                                {
                                    depth--;
                                    if (depth == 0) complete = true;
                                }
                                json.append(c);
                            }
                            if (complete) break;
                            if (quoted) throw error(instruction.line, "JSON strings cannot contain literal line breaks");
                            json.append('\n');
                            if ((cancelled.get()) || (isInterrupted())) throw new InterruptedException();
                            argument = reader.readLine();
                            if (argument == null) throw error(instruction.line, "Unterminated JSON object");
                            line++;
                        } while (true);
                        instruction.json = json.toString();
                        parseRequest(instruction.json, instruction.line);
                        break;
                    default:
                        throw error(line, "Unknown keyword: " + keyword);
                }
                instructions.add(instruction);
            }
        }
        if (loops.isEmpty() == false) throw error(instructions.get(loops.peek()).line, "startloop has no matching endloop");
    }

    /**
     * Creates a BridgeRequest instance from the specified JSON string.
     *
     * @param   json            JSON String from which to create BridgeRequest instance
     * @param   line            number of line in script file that is being parsed
     *
     * @return  created BridgeRequest instance
     */
    private static BridgeRequest parseRequest(String json, int line)
    {
        JsonObject object = JsonUtils.parseJsonObject(json, null);
        if (object == null) throw error(line, "Invalid JSON object");
        String name = JsonUtils.getString(object, "request");
        String[] parts = (name == null) ? new String[0] : name.split(":", -1);
        if (parts.length != 3) throw error(line, "request must have the form command:module:action");
        for (int i=0; i<parts.length; i++)
        {
            parts[i] = parts[i].trim().toLowerCase(Locale.ROOT);
            if (parts[i].isEmpty()) throw error(line, "request contains an empty component");
        }
        if ("command".equals(parts[0]) == false) throw error(line, "execute requires a command request");
        return BridgeRequest.create(BridgeRequest.Protocol.REST, JsonUtils.getString(object, "id"),
            parts[0], parts[1], parts[2], object.get("data"));
    }

    /**
     * Returns a Java @c IllegalArgumentException instance containing the specified contents.
     *
     * @param   line            line number to report in exception
     * @param   message         message to include in exception
     *
     * @return  Java @c IllegalArgumentException instance
     */
    private static IllegalArgumentException error(int line, String message)
    {
        return new IllegalArgumentException("line " + line + ": " + message);
    }

    /***********************************************************************************************
     * CLASSES / ENUMERATORS / INTERFACES
     **********************************************************************************************/

    /**
     * Helper class storing a single instruction.
     */
    private static final class Instruction
    {
        /** Line in script file. */
        final int line;

        /** Instruction keyword. */
        final String keyword;

        /** Instruction value. */
        long value;

        /**
         * Index of @e endloop instruction.
         *
         * This variable is only set for @e startloop instructions.
         */
        int match;

        /** JSON contents of instruction. */
        String json;

        /**
         * Constructs a new Instruction instance.
         *
         * @param   line            line in script file
         * @param   keyword         instruction keyword
         */
        Instruction(int line, String keyword)
        {
            this.line = line;
            this.keyword = keyword;
        }
    }

    /**
     * Helper class controlling loop execution.
     */
    private static final class Loop
    {
        /** Index specifying start of loop. */
        final int start;

        /** Number of remaining loop iterations. */
        long remaining;

        /**
         * Constructs a new Loop instance.
         *
         * @param   start           index specifying start of loop
         * @param   remaining       number of remaining loop iterations
         */
        Loop(int start, long remaining)
        {
            this.start = start;
            this.remaining = remaining;
        }
    }

    /**
     * Defines BridgeService request callback contract.
     *
     * This interface must be implemented by the BridgeService class to allow forwarding bridge
     * requests to the bridge service.
     */
    public interface RequestHost
    {
        /**
         * Executes a bridge request.
         *
         * @param   request         bridge request to be executed
         *
         * @return  JsonResponse instance containing response data
         */
        JsonResponse execute(BridgeRequest request);
    }
}
