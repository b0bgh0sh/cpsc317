package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.io.*;

import javax.swing.JOptionPane;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        // TODO Add your code here
        String Response = null;
	    try
    	{
            socket = new Socket(host, port);
            System.out.println("Socket established"); // Connection Establishedd
            output = new PrintWriter(socket.getOutputStream(), true);
            input = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));
            System.out.println("Input, output stream created");
            Status status = Status.readStatus(input);
            if (status.getStatusCode() != 220)
            {
                DictConnectionException E = new DictConnectionException(
                                                status.getDetails());
                throw E;
            }
        	//System.out.println(Response);
    	}
    	catch(Exception e)
    	{
            System.out.println("An exception happens"); // Encountered Error
            DictConnectionException E = new DictConnectionException(e);
            throw E;
    	}

    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        // TODO Add your code here
      	String QuitCmd = "QUIT\r\n";
        String Response = null;
        try
        {
            output.println(QuitCmd);
            Response = input.readLine();
            System.out.println(Response);
        }
        catch (Exception e){}
        finally
        {
            try
            {
                socket.close();
            }
            catch (Exception e) {}
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        // TODO Add your code here
        String DatabaseName = database.getName();
        String cmd = "DEFINE " + DatabaseName + " \"" + word + "\"\r\n";
        String response = null;
        try
        {
            output.println(cmd);
            // Parse status
            Status status = Status.readStatus(input);
            String details = status.getDetails();
            if (status.getStatusCode() == 552) // No match found
            {
                JOptionPane.showMessageDialog(null, "No match found", "No match", 
                                              JOptionPane.ERROR_MESSAGE);
                return set;
            }
            if (status.getStatusCode() != 150)
            {
                DictConnectionException E = new DictConnectionException(details);
                throw E;
            }
            // Get how many definition in total
            String ParsedDetails[] = DictStringParser.splitAtoms(details);
            int num = Integer.parseInt(ParsedDetails[0]);
            for (int i = 0; i < num; i++)
            {
                // parse status
                status = Status.readStatus(input);
                details = status.getDetails();
                if (status.getStatusCode() != 151)
                {
                    DictConnectionException E = new
                                            DictConnectionException(details);
                    throw E;
                }
                // get the database from which the definition retrieved
                String Details[] = DictStringParser.splitAtoms(details);
                // word is Details[0]
                // database name is Details[1]
                Database d = databaseMap.get(Details[1]);
                Definition definition = new Definition(Details[0], d);
                // read and get definition
                while (true)
                {
                    response = input.readLine();
                    if (response.equals(".")) break;
                    //System.out.println(response);
                    definition.appendDefinition(response);
                }
                String D = definition.getDefinition();
                //System.out.println("Definiton:");
                //System.out.println(D);
                set.add(definition);
            }
            // Check completion Status
           status  = Status.readStatus(input);
           if (status.getStatusCode() != 250)
           {
               DictConnectionException E = new DictConnectionException
                                           (status.getDetails());
               throw E;
           }
        }
        catch (Exception e)
        {
            DictConnectionException E = new DictConnectionException(e);
            throw E;
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        String DatabaseName = database.getName();
        String StrategyName = strategy.getName();
       // String cmd = "MATCH " + DatabaseName + " \"" + StrategyName + "\"\r\n";
        String cmd = "MATCH " + DatabaseName + " \"" + StrategyName + "\" ";


        //if (word.split("\"").length > 1)
        if (word.split(" ").length > 1)
          cmd += ('"' + word + '"') + "\r\n";
        else
          //cmd += word;
          cmd += " " + word + "\r\n";

        try {
          //System.out.println(cmd);
          output.println (cmd);
          //Integer code = Status.getStatusCode();
          Status status = Status.readStatus(input);
          int code = status.getStatusCode();
          String details = status.getDetails();
          if (code == 552)
            return set;
          if (code != 152) {
            DictConnectionException E = new DictConnectionException(details);
            throw E;
          }
          //Integer numOfMatches = Integer.parseInt(details.split("\"")[0]);
          String ParsedDetails[] = DictStringParser.splitAtoms(details);
          Integer numOfMatches = Integer.parseInt(ParsedDetails[0]);
          while (numOfMatches-- > 0) {
            String[] matches = DictStringParser.splitAtoms(input.readLine());
            set.add(matches[1]);
          }
          // Read the finish symbol '.'
          input.readLine();
          // Check completion Status
          status  = Status.readStatus(input);
          if (status.getStatusCode() != 250)
          {
            DictConnectionException E = new DictConnectionException
                                           (status.getDetails());
            throw E;
          }
        } catch (Exception e) {
            throw new DictConnectionException(e);
        }
        return set;
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();

        // TODO Add your code here
        String SHOWDB = "SHOW DB\r\n";
        String Response = null;
        try
        {
             // Send command
             output.println(SHOWDB);
             // Receive status response
             Status status = Status.readStatus(input);
             if (status.getStatusType() == 5) // No databases present
             {
                 DictConnectionException E = new DictConnectionException
                                                 ("No databases present");
                 throw E;
             }
             else // Read databases
             {
                 do
                 {
                     Response = input.readLine();
                     //System.out.println(Response);
                     if (Response.equals(".")) break;

                     String ParsedResponse[] = DictStringParser.splitAtoms(Response);
                     Database d = new Database(ParsedResponse[0], ParsedResponse[1]);
                     databaseMap.put(ParsedResponse[0], d);
                 } while (true);
             }
             // Check completion status
             status = Status.readStatus(input);
             if (status.getStatusType() != 2)
             {
                 DictConnectionException E = new DictConnectionException
                                                 (status.getDetails());
                 throw E;
             }

        }
        catch (Exception e)
        {
             DictConnectionException E = new DictConnectionException (e);
             throw E;
        }
        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        String SHOWSTRAT = "SHOW STRAT\r\n";
        String Response = null;
        //output.println(SHOWSTRAT);
        try {
          //Integer code = Status.getStatusCode();
          output.println(SHOWSTRAT);
          Status status = Status.readStatus(input);
          int code = status.getStatusCode();
          String details = status.getDetails();
          if (code == 555)
            return set;
          if (code != 111) {
            DictConnectionException E = new DictConnectionException(details);
            throw E;
          }
          // Integer numOfStrat = Integer.parseInt(details.split("\"")[0]);
          String ParsedDetails[] = DictStringParser.splitAtoms(details);
          Integer numOfStrat = Integer.parseInt(ParsedDetails[0]);
          while(numOfStrat-- > 0){
            Response = input.readLine();
            //System.out.println("strategy: ");
            //System.out.println(Response);
            //String[] strats = DictStringParser.splitAtoms(input.readLine());
            String[] strats = DictStringParser.splitAtoms(Response);
            set.add(new MatchingStrategy(strats[0], strats[1]));
          }
          // Read the finish symbol '.'
          input.readLine();
          // Check completion status
          System.out.println("complete");
          status  = Status.readStatus(input);
          if (status.getStatusCode() != 250)
          {
              DictConnectionException E = new DictConnectionException
                                          (status.getDetails());
              throw E;
          }

        } catch (Exception e) {
            DictConnectionException E = new DictConnectionException (e);
            throw E;
        }
        return set;
    }

}
