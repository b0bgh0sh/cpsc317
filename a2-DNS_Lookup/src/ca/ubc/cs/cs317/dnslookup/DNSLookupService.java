package ca.ubc.cs.cs317.dnslookup;

import java.io.Console;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.net.DatagramPacket;
import java.lang.Exception;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();
    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
//        myDebugMethod();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                  	System.out.print("DNSLOOKUP> ");
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {

        DNSNode node = new DNSNode(hostName, type);
        printResults(node, getResults(node, 0));
    }


    /**
     * Find a nearest name server in cache for a DNS query.
     * 
     * @param node   The DNS query node need to be addressed.
     * @param robust Return the root server if any exception occur if robuts
     *               is true, otherwise return null. If the address is for 
     *               the first question packet of a query, robust should be
     *               true meaning we send it to a root server by dafualt.
     *               But if it's not the first packet then we shouldn't return
     *               root server, otherwise we may have an dead loop.
     *
     * @return An ArrayList of type A resource record of nearest name server.
     */
    private static ArrayList<ResourceRecord> getNextNameServer(DNSNode node, 
                                                         boolean robust) {
        String domainName = node.getHostName();
        ArrayList<ResourceRecord> results = new ArrayList();

        while (true) {
            Set<ResourceRecord> records = cache.getCachedResults(
                                      new DNSNode(domainName, RecordType.NS));
            // If we don't have Name Server corresponding to this level
            // we then check for domainName of next level
            if (records.isEmpty() == true) {
                // if it's already the top level, return a set containing only
                // root server
                if (domainName.indexOf('.') == -1) {
                    results.add(new ResourceRecord("rootServer",
                                                   RecordType.A,
                                                   0,
                                                   rootServer));
                    return results;
                }
                else {
                    String [] namePieces = domainName.split("\\.",2);
                    domainName = namePieces[1];
                }
            }
            else { // we have name server of this level, now we need to get
                   // it's IP address
                // First check if we have a IP address in cache
                for(ResourceRecord recordNS: records) {
 
                    Set<ResourceRecord> addressNSSet = cache.getCachedResults(
                           new DNSNode(recordNS.getTextResult(), RecordType.A));

                    for (ResourceRecord addrRecord: addressNSSet) {
                        results.add(addrRecord);
                    }
                }

                // If we do, just return them.
                if (results.isEmpty() == false) {
                    return results;
                }

                // Otherwise we don't have the ip address for the server, we 
                // need to look it up ourselves.
                for(ResourceRecord recordNS: records) {
 
                    Set<ResourceRecord> addressNSSet = getResults(
                      new DNSNode(recordNS.getTextResult(), RecordType.A), 0);

                    for (ResourceRecord addrRecord: addressNSSet) {
                        results.add(addrRecord);
                    }
                }

                // If now we have results, return them
                if (results.isEmpty() == false) {
                    return results;
                }
                // If we still don't get the addresses, it must be an exception
                // But if robust == true, we keep looking upper level to find
                // a name server.
                else if (robust == false) {
                    return results; // results is empty here
                }
            } 
        } 
    }     


    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regarding CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records corresponding to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }


        // TODO To be completed by the student
        // First check cache for answer.
        Set<ResourceRecord> answer = null;
        answer = cache.getCachedResults(node);
        if (answer.isEmpty() == false) return answer;


        // We should also check for CNAME record in cache, if it has a
        // CNAME, check the CNAME's answer
        answer = cache.getCachedResults(new DNSNode(node.getHostName(), 
                                        RecordType.CNAME));
        if (answer.isEmpty() == false) {
            ResourceRecord result = (ResourceRecord)answer.toArray()[0];
            return getResults(
                      new DNSNode(result.getTextResult(), node.getType()),
                      indirectionLevel + 1);
               
        }

        // Now we should determine which name server to send to.
        // We should check the domain name level by level to determined
        // the closest nameserver to send query.
        ArrayList<ResourceRecord> setNSAddr = getNextNameServer(node, true);
        if (setNSAddr.isEmpty() == true) {
            // This actually would not happen, but just for safe.
            retrieveResultsFromServer(node, rootServer);
        }
        else {
            int retCode = -1;
            // Go over all name server until one success.
            for (ResourceRecord nameServerAddr: setNSAddr) {
                retCode = retrieveResultsFromServer(node, 
                                          nameServerAddr.getInetResult());
                if (retCode != -1) break;
            }
          
        }

 
 
        // Check if we have answer
        answer = cache.getCachedResults(node);
        if (answer.isEmpty() == true) {
          // If don't check for CNAME
          answer = cache.getCachedResults(new DNSNode(node.getHostName(), 
                                          RecordType.CNAME));
          if (answer.isEmpty() == false) {
            ResourceRecord result = (ResourceRecord)answer.toArray()[0];
//            if (result.getTTL() == -1) return Collections.emptySet();
  //          else {
            return getResults(
                      new DNSNode(result.getTextResult(), node.getType()), 
                      indirectionLevel + 1);
  //          }
          }
          else { // If we don't have a CNAME neither, it's an exception.
              return Collections.emptySet();
          }
        } //else {
//            ResourceRecord result = (ResourceRecord)answer.toArray()[0];
//            if (result.getTTL() == -1) return Collections.emptySet();
            //  return answer;
       //   }

        return answer;
    }


    /**
     * Generate a transactionID.
     *
     * @return A transactionID.
     */
    private static short getTransactionID() {
        return (short) (random.nextInt() & 0xFFFF);
    }

    /**
     * Form a query packet given a particular query.
     *
     * @param node          The DNSNode of given query.
     * @param transactionID The ID of this transaction.
     *
     * @return The corresponding datagram packet.
     */
    private static DatagramPacket formQuestionPacket(DNSNode node,
                                                     int transactionID,
                                                     InetAddress server) {

        byte [] query = new byte[512];

        // ID
        query[0] = (byte)(transactionID >> 8);
        query[1] = (byte)(transactionID);

        // Query
        query[2] = 0;
        query[3] = 0;

        // Question
        query[4] = 0;
        query[5] = 1;

        // Answer
        for (int i = 6; i < 13; i++) {
          query[i] = 0;
        }

        int index = 12; // Setting index for Dynamic Purposes

        // Domain Name
        String[] hostName = node.getHostName().split("\\.");
        for (int i = 0; i < hostName.length; i++) {
          query[index++] = (byte)hostName[i].length();
          byte[] bytes = hostName[i].getBytes();
          for (int j = 0; j < bytes.length; j++) {
            query[index++] = bytes[j];
          }
        }
        query[index++] = 0;

        // Type
        query[index++] = 0;
        query[index++] = (byte)node.getType().getCode();

        // IN
        query[index++] = 0;
        query[index++] = 1;
        byte[] newQuery = new byte[index];
        for (int i = 0; i < index; i++) {
          newQuery[i] = query[i];
        }
        DatagramPacket queryPacket = new DatagramPacket(newQuery,index,
                                                 server, DEFAULT_DNS_PORT);


        return queryPacket;
    }

    /**
     * Receive a resposne.
     * @return The received datagram packet.
     */
    private static DatagramPacket receiveResponse() {

        byte [] responseBuf = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(responseBuf,1024);

        try {
            socket.receive(responsePacket);
        }
        catch (Exception e){
            return null;
        }
        return responsePacket;
    }

 
    /**
     * Output the trace of a query.
     *
     * @param node          DNSNode of query
     * @param server        The InerAddress of the server.
     * @param transactionID The transaction ID
     */
    private static void traceQuery(DNSNode node, InetAddress server, 
                                   short transactionID) {
        if (verboseTracing == true) {
            System.out.printf("\n\nQuery ID     %d %s  %s --> %s\n",
                              transactionID&0xff, node.getHostName(), 
                              node.getType(),
                              server.toString().substring(1));
        }
    }
   

    /**
     * Send the question packet to the server and received response.
     *
     * @param node          DNSNode of query
     * @param server        The InerAddress of the server.
     * @param transactionID The transaction ID
     *
     * @return The received response packet
     */
    private static DatagramPacket sendQuestionGetResponse(
                                              DNSNode node,
                                              InetAddress server,
                                              short transactionID) {

        DatagramPacket questionPacket= formQuestionPacket(node, transactionID,
                                                          server);

        try {
            traceQuery(node, server, transactionID);
            socket.send(questionPacket);
            DatagramPacket responsePacket = receiveResponse();
            // If resposne is null, try again
            if (responsePacket == null) {
                socket.send(questionPacket);
                traceQuery(node, server, transactionID);
                responsePacket = receiveResponse();
            }
            return responsePacket;
        }
        catch (Exception e){
            return null;
        }
    }


    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     *
     * @return 0 if no exception happens, 
     *         -1 if couldn't reveive response, so the caller may change to
     *         another server.
     *         -2 otherwise.
     */
    private static int retrieveResultsFromServer(DNSNode node, InetAddress server) {

        // TODO To be completed by the student
        // Form a query datagram packet.
        short transactionID = getTransactionID();
//        DatagramPacket questionPacket= formQuestionPacket(node, transactionID,
//                                                          server);
        

        // Send the question packet and get response.
        DatagramPacket responsePacket = sendQuestionGetResponse(node, server,
                                                              transactionID);
        if (responsePacket == null) {
            return -1;
        }


        // Now check the header of resposne.
        DNSHeader header = null;
        // Keep receiving until we get packet with right transaction ID.
        while (true) {
            header = DNSPacketParser.parseHeader(responsePacket);
            if (header == null) return -2;
            if (header.getTransactionID() != transactionID) {
            // In this case we should ignore the packet and
            // try to get a correct one.
                responsePacket = receiveResponse();
                if (responsePacket == null) return -2;
            }
            else {
                break;
            }
        }


        // Check header for exception
        if (header.getRCODE() != 0) return -2;
        if (header.getTC() == true) return -2;
        if (header.getAA() == true && header.getANCOUNT() == 0) return -2;
        if (header.getQR() == false ||
            header.getOPCODE() != DNSHeader.OPCODE_QUERY ||
            header.getQDCOUNT () != 1)
            return -2;

        if (verboseTracing == true) {
            System.out.printf("Response ID: %d Authoratative = %s\n", 
                              transactionID & 0xFF, 
                              String.valueOf(header.getAA()));
        }

        // Now parse the resource record in response
        int err = DNSPacketParser.parseResponse(node, responsePacket,
                                                verboseTracing);
        if (err != 0) return -2;


        // Now based on our cached record, determine what we do next.
        // I think when server is authoratative, we should return
        // and let getResults() decide what to do next, for example return 
        // answers or start a new recursion for a CNAME.
        if (header.getAA() == true) {
            // If AA is true, ANCOUNT is not 0 and nothing wrong when decoding
            // we get the answer
            return -2;
        }
        else {
            // If server it's not authoratative, we should get a nameserver
            // or exception
            // get the name server and call retrieveResultsFromServer for
            // this new server. Also, ip address of name server may be not
            // in cache.
            if (header.getNSCOUNT() == 0) {
                return -2;
            }
            // we should have a name server for next level.
            ArrayList<ResourceRecord> setNSAddr = getNextNameServer(node, false);
            if (setNSAddr.isEmpty() == true) {
                return -2;
            }
 
            int retCode = -2;
            // Go over all name server until one success.
            for (ResourceRecord nameServerAddr: setNSAddr) {
                retCode = retrieveResultsFromServer(node, 
                                          nameServerAddr.getInetResult());
                if (retCode != -1) break;
            }

            // If we couldn't connect to any name server, it's more likely
            // because our internet is down.
            if (retCode == -1) return -2;

            return retCode;
        }
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), record.getTTL(), record.getTextResult());
        }
    }

    /**
     * Add a resource record into cache. This method is built so that my
     * DNSPacketParser could add resource record into cache.
     *
     * @param record The resource record to be added into cache.
     */
    public static void addToCache (ResourceRecord record) {
        cache.addResult(record);
    }

    /**
     * A small method help me debug decoding response
     */
    private static void myDebugMethod() {
        // Query www.ugrad.cs.ubc.ca's ipv4 address
        String domainName1 = "www.ugrad.cs.ubc.ca";
        byte [] query1 = {0x2b,0x2b,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x03,0x77,0x77,0x77,0x05,0x75,0x67,0x72,
                           0x61,0x64,0x02,0x63,0x73,0x03,0x75,0x62,0x63,0x02,
                           0x63,0x61,0x00,0x00,0x01,0x00,0x01};
        // Query finance.google.ca IPV4 address
        String domainName2 = "finance.google.ca";
        byte [] query2 = {0x2b,0x2b,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x07,0x66,0x69,0x6e,0x61,0x6e,0x63,0x65,
                           0x06,0x67,0x6f,0x6f,0x67,0x6c,0x65,0x02,0x63,0x61,
                           0x00,0x00,0x01,0x00,0x01};
        // Query blueberry.ugrad.cs.ubc.ca
        String domainName3 = "blueberry.ugrad.cs.ubc.ca";
        byte [] query3 = {0x2b,0x2b,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x09,0x62,0x6c,0x75,0x65,0x62,0x65,0x72,
                           0x72,0x79,0x05,0x75,0x67,0x72,0x61,0x64,0x02,0x63,
                           0x73,0x03,0x75,0x62,0x63,0x02,0x63,0x61,0x00,0x00,
                           0x01,0x00,0x01};
        // Query prep.ai.mit.edu
        String domainName4 = "prep.ai.mit.edu";
        byte [] query4 = {0x2b,0x2b,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x04,0x70,0x72,0x65,0x70,0x02,0x61,0x69,
                           0x03,0x6d,0x69,0x74,0x03,0x65,0x64,0x75,0x00,0x00,
                           0x01,0x00,0x01};
       // Query www.stanford.edu
        String domainName5 = "www.stanford.edu";
        byte [] query5 = {0x2b,0x2b,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x03,0x77,0x77,0x77,0x08,0x73,0x74,0x61,
                           0x6e,0x66,0x6f,0x72,0x64,0x03,0x65,0x64,0x75,0x00,
                           0x00,0x01,0x00,0x01};


        byte [] query = query1;
        String domainName = domainName1;

        byte [] responseBuf = new byte [1024];

        DatagramPacket queryPacket = new DatagramPacket(query,query.length,
                                                 rootServer, DEFAULT_DNS_PORT);
        DatagramPacket responsePacket = new DatagramPacket(responseBuf,1024);
        try {
            socket.send(queryPacket);

            socket.receive(responsePacket);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
        byte [] buf = responsePacket.getData();
        for(int i = 0; i < responsePacket.getLength(); i++){
            System.out.printf("%02X ", buf[i]);
            if (i % 32 == 31) System.out.printf("\n");
        }
        System.out.printf("\n");

        DNSNode queryNode = new DNSNode(domainName,
                                        RecordType.getByCode(1));
        DNSPacketParser.parseResponse(queryNode, responsePacket,
                                       true);
    }
}
