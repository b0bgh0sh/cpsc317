package ca.ubc.cs.cs317.dnslookup;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.net.DatagramPacket;
import java.lang.Exception;


public class DNSPacketParser {

    /**
     * Print information in a DNSHeader. Used to help me debug
     *
     * @param header The header need to print
     */
    private static boolean verboseTracing = false;

    private static void printHeader(DNSHeader header) {
        System.out.println("Transaction ID: " + header.getTransactionID());
        System.out.println("QR: " + header.getQR());
        System.out.println("OPCODE: " + header.getOPCODE());
        System.out.println("AA: " + header.getAA());
        System.out.println("TC: " + header.getTC());
        System.out.println("RD: " + header.getRD());
        System.out.println("RA: " + header.getRA());
        System.out.println("RCODE: " + header.getRCODE());
        System.out.println("QDCOUNT: " + header.getQDCOUNT());
        System.out.println("ANCOUNT: " + header.getANCOUNT());
        System.out.println("NSCOUNT: " + header.getNSCOUNT());
        System.out.println("ARCOUNT: " + header.getARCOUNT());
    }


    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }


    /**
     * A helpful class so I can decode domain name and change the offset
     * to where next part starts at the same time.
     */
    static private class Offset {
        public int offset;
        public Offset (int _offset) {
            offset = _offset;
        }
    }

    /**
     * Read a short integer
     * 
     * @param data         Array of bytes of received data.
     * @param offsetObject The offset where the integer begins, when finish 
     *                     parsing the offset will be set to where next part 
     *                     starts.
     *
     * @return A short integer. And set offset to where next part
     *         starts.
     */
    private static short getShort(byte [] data, Offset offsetObject) {
       if (offsetObject.offset + 2 > data.length) 
            throw new NullPointerException("getint: offset beyond data length");

       int offset = offsetObject.offset;
        short ret = (short)(((data[offset] & 0xFF)<<8) + 
                          (data[offset+1] & 0xFF)); 
        offsetObject.offset = offset + 2;
        return ret;
    }     

    /**
     * Read a 4 byte integer
     * 
     * @param data         Array of bytes of received data.
     * @param offsetObject The offset where the integer begins, when finish 
     *                     parsing the offset will be set to where next part 
     *                     starts.
     *
     * @return A 4 byte integer. And set offset to where next part
     *         starts.
     */
    private static int getInt(byte [] data, Offset offsetObject) {
        if (offsetObject.offset + 4 > data.length) 
            throw new NullPointerException("getint: offset beyond data length");

        int offset = offsetObject.offset;
        int ret = ((data[offset] & 0xFF)<<24) + ((data[offset+1] & 0xFF)<<16)
                 +((data[offset+2]&0xFF)<<8) + (data[offset+3] & 0xFF);
        offsetObject.offset = offset + 4;
        return ret;
    }     

    /**
     * Decode a character string. And set the offset to where next part 
     * starts.
     *
     * @param data         Array of bytes of received data.
     * @param offsetObject The offset where character string  begins, when 
     *                     finish. parsing the offset will be set to where
     *                     next part starts.
     *
     * @return The character string. And set offset to where next part
     *         starts.
     */
    private static String decodeCharacterString(byte [] data, 
                                                Offset offsetObject) {
        if (offsetObject.offset >= data.length) {
            throw new NullPointerException(
                      "decodeCharacterString: offset beyond data's length");
        }

    
        // Read Length 
        String ret = "";
        int numberToRead = data[offsetObject.offset] & 0xFF;
        if (offsetObject.offset + numberToRead > data.length) {
            throw new NullPointerException(
                      "decodeCharacterString: offset beyond data's length");
        }


        // Read String
        int offset = offsetObject.offset;
        for(int i = 1; i <= numberToRead; i++) {
            if (offset + i >= data.length) {
                throw new NullPointerException(
                          "decodeDomainName: offset beyond data's length");
            }

            char character = (char) data[offset + i];
            ret = ret + Character.toString(character); 
        }
        

        offsetObject.offset = offset + numberToRead + 1;
    
        return ret;
    }

    /**
     * Decode the domain name.
     *
     * @param data         Array of bytes of received data.
     * @param offsetObject The offset where domain name begins, when finish 
     *                     parsing the offset will be set to where next part 
     *                     starts.
     *
     * @return The string of domain name. And set offset to where next part
     *         starts.
     */
    private static String decodeDomainName(byte [] data, Offset offsetObject) {

        int offset = offsetObject.offset;

        if (offset >= data.length) {
            throw new NullPointerException(
                      "decodeDomainName: offset beyond data's length");
        } 

        String ret = "";
      
        // Decode domain name byte by byte.
        while (offset < data.length) {

            if (data[offset] == 0) { // Domain name is end.
                offsetObject.offset = offset + 1;
                if (ret == "") return ret; // Occurs when name starts with 0
                                           // The caller method should check 
                                           // this exception

                // According to my code, when finish decoding, there will be
                // a redundant '.' at the end of ret, we need to remove it.    
                return ret.substring(0,ret.length() - 1);
            }
            else if (((int)data[offset] & 0xC0) == 0xC0) { // It's a pointer

                if (offset + 1 == data.length) {
                    throw new NullPointerException(
                              "decodeDomainName: offset beyong data's length");
                }

                int newOffset = (((int)data[offset] & 0x3F) << 8) +
                                ((int)data[offset+1] & 0xFF); 

                // go to the position pointer refers to and add it to the end
                Offset newOffsetObject = new Offset (newOffset);
                ret = ret + decodeDomainName(data, newOffsetObject);

                // change the offset to where next part starts
                offsetObject.offset = offset + 2;

                return ret;
            }
            else if (((int)data[offset]&0xC0) == 0) { //number of bytes to read
                int numberToRead = (int)data[offset] & 0x3F;
                
                for(int i = 1; i <= numberToRead; i++) {
                    if (offset + i >= data.length) {
                        throw new NullPointerException(
                              "decodeDomainName: offset beyond data's length");
                    }

                    char character = (char) data[offset + i];
                    ret = ret + Character.toString(character); 
                }

                ret = ret + ".";
                offset = offset + numberToRead + 1;
            }
            else { // something wrong with the first 2 bits
                offsetObject.offset = offset + 1;
                return null;
            }
        }

        // If program is executing code below, then we have run out of data 
        // but still didn't get the whole domain. It's an exception.
        throw new NullPointerException(
                  "decodeDomainName: offset beyond data's length");
    }

    /**
     * Parse the question section of a response datagram. Return the
     * corresponding DNSNode
     * 
     * @param data   Array of bytes of received data.
     * @param offsetObject The offset where question section starts, when 
     *                     finish parsing, the offset will be set to where 
     *                     next section starts.
     *
     * @return The corresponding DNSNode, and set offset to where next part
     *         starts. And return null if the packat has any exception
     */
    private static DNSNode parseQuestion(byte [] data, Offset offsetObject) {

        if (offsetObject.offset >= data.length)
        {
            throw new NullPointerException(
                      "parseQuestion: offset beyond data's length");
        }

        String domainName = "";
        // Rirst get the domain name in question section
        try {
            domainName = decodeDomainName(data, offsetObject);
        }
        catch (NullPointerException e) {
            throw e;
        }

        if (domainName == "") {
            return null;
        }
        //System.out.println("We decode that domain name is " + domainName);
        //System.out.printf("The offset is now at %d\n", offsetObject.offset);
       
        int offset = offsetObject.offset;
        // read the type and class
        int typeCode, classCode;
        try {
            typeCode = getShort(data, offsetObject); 
            classCode = getShort(data, offsetObject); 
        }
        catch (NullPointerException e) {
            return null;
        }
        // assert class code is 1
        if (classCode != 1) {
            return null;
        }
    
        
        // Return corresponding DNSNode
        DNSNode ret = new DNSNode(domainName, RecordType.getByCode(typeCode));
        return ret;
    }
 
    /** 
     * Parse resource record whose type wasn't included in RecordType. And set
     * offset to where next part begins.
     * 
     * @param data         array of bytes of data received.
     * @param offsetObject The offset where the recource record start. It will
     *                     be set to where next record starts after parsing.
     * @param typeCode     The type code of the resource record.
     * @param dataLen      The length of the resource record
     *
     * @return Return the parsed resource record. If something went wrong, 
               return a string saying "Data couldn't parsed" so not to
               break the process.
     */
 
    private static String parseOtherTypeRR(byte [] data, Offset offsetObject, 
                                    int typeCode, short dataLen) {
    
        String ret = "";
        int offset = offsetObject.offset;
        switch (typeCode) {

            case 7 : // MB
            case 8 : // MG
            case 9 : // MR
            case 12 : // PTR
                 try {
                     ret = decodeDomainName(data, offsetObject);
                 }
                 catch (NullPointerException e) {
                     offsetObject.offset = dataLen + offset;
                     return "Couldn't parse it";
                 }
                 return ret;

 
            case 10 : // NULL
                 ret = "It's a NULL record: ";
                 offsetObject.offset += dataLen;
                 try {
                     for(int i = 0; i < dataLen; i++) {
                         char character = (char) data[offset + i];
                         ret = ret + Character.toString(character); 
                     }
                 }
                 catch (Exception e) {
                     offsetObject.offset = dataLen + offset;
                     return "It's a NULL record: couldn't parse it";
                 }
                 offsetObject.offset = dataLen + offset;
                 return ret;


            case 11 : // WKS
                 try {
                     int ADDR = getInt(data, offsetObject);
                     byte PROTOCAL = data[offsetObject.offset + 1];
                     offsetObject.offset += 1;
                     ret = "ADDR: " + Integer.toString(ADDR) + "; PROTOCAL: "
                           + Integer.toString(PROTOCAL & 0xFF) + "; BITMAP: ";
                     for (int i = 3; i < dataLen; i++) {
                          ret += Integer.toString(data[offset + i]&0xFF);
                     }
                 }
                 catch (Exception e) {
                     offsetObject.offset = dataLen + offset;
                     return "Couldn't parse it";
                 }
                 offsetObject.offset = dataLen + offset;
                 return ret;

            case 13 : // HINFO
                 String CPU = "", OS = "";
                 try {
                     CPU = decodeCharacterString(data, offsetObject);
                     OS = decodeCharacterString(data, offsetObject);
                 }
                 catch (NullPointerException e) {
                    offsetObject.offset = dataLen + offset;
                    return "Couldn't parse it";
                 }
                 ret = "CPU: " + CPU + "; OS: " + OS;
                 return ret;     


            case 14 : // MINFO
                String RMAILBX = "", EMAILBX = "";
                try {
                     RMAILBX = decodeDomainName(data, offsetObject);
                     EMAILBX = decodeDomainName(data, offsetObject);
                 }
                 catch (NullPointerException e) {
                     offsetObject.offset = dataLen + offset;
                     return "Couldn't parse it";
                 }
                 ret = "RMAILBX: " + RMAILBX + "; EMAILBX: " + EMAILBX;
                 return ret;


            case 16 : // TXT  
                try {
                    while (offsetObject.offset < dataLen + offset) {
                        ret += (decodeCharacterString(data, offsetObject) 
                                + "; ");
                    }
                }
                catch (Exception e) {
                    offsetObject.offset = dataLen + offset;
                    return "Couldn't parse it";
                }
                return ret;


            default : return "Couldn't parse it: illegal type";
        }
    }

    /**
     * Parse a rescource record.
     * 
     * @param data         array of bytes of data received.
     * @param offsetObject The offset where the recource record start. It will
     *                     be set to where next record starts after parsing.
     *
     * @return Return the parsed resource record
     */
    private static ResourceRecord parseResourceRecord(byte [] data, 
                                                      Offset offsetObject) {
        // Get the domain name
        String domainName = "";
        try {
            domainName = decodeDomainName(data, offsetObject);
        }
        catch (NullPointerException e) {
            return null;
        }
        if (domainName == "") return null;


        // Read type and class
        if (offsetObject.offset + 4 > data.length) {
            throw new NullPointerException (
                      "parseResourceRecord: offset beyond length of data");
        }
        int typeCode, classCode;
        try {
            typeCode = getShort(data, offsetObject); 
            classCode = getShort(data, offsetObject); 
        }
        catch (NullPointerException e) {
            return null;
        }
        // assert class code is 1
        if (classCode != 1) {
            return null;
        }


        // Read TTL
        if (offsetObject.offset + 4 > data.length) {
            throw new NullPointerException (
                      "parseResourceRecord: offset beyond length of data");
        }
        
        int ttl;
        try {
            ttl = getInt(data, offsetObject); 
        }
        catch (NullPointerException e) {
            return null;
        }     
        if (ttl == 0) { // When TTL==0. it means we shouldn't cache it. But
                        // due to the design of retrieveResultsFromServer
                        // we have to. So change the TTL to a small number to
                        // cache it.
            ttl = 100;
        }

        // Read length of RDATA
        if (offsetObject.offset + 2 > data.length) {
            throw new NullPointerException (
                      "parseResourceRecord: offset beyond length of data");
        }
        short dataLength;
        try {
            dataLength = getShort(data, offsetObject); 
        }
        catch (NullPointerException e) {
            return null;
        }

   
        // Read RDATA 
        RecordType type = RecordType.getByCode(typeCode);
        ResourceRecord newRR;
        switch (type) {

            case A : 
            case AAAA :            
                int ipAddrLen = type == RecordType.A ? 4 : 16;
                if (offsetObject.offset + ipAddrLen > data.length) {
                    throw new NullPointerException (
                       "parseResourceRecord: offset beyond length of data");
                }
                byte [] ipAddrByte = new byte[ipAddrLen];
                System.arraycopy(data, offsetObject.offset
                                 , ipAddrByte, 0, ipAddrLen);
                InetAddress ipAddress = null;
                try {
                   ipAddress = InetAddress.getByAddress(ipAddrByte);
                }
                catch (UnknownHostException e) {
                   return null;
                }
                offsetObject.offset += ipAddrLen;
                newRR = new ResourceRecord(domainName, type, ttl, ipAddress);
                verbosePrintResourceRecord(newRR, typeCode);              
                return newRR;

   
            case CNAME:
            case NS :
                String nameServer = "";
                try {
                    nameServer = decodeDomainName(data, offsetObject);
                }
                catch (NullPointerException e) {
                    return null;
                }
                if (nameServer == "") {
                    return null;
                }
                newRR = new ResourceRecord(domainName, type, ttl, nameServer);
                verbosePrintResourceRecord(newRR, typeCode);              
                return newRR;


            case MX : 
                if (offsetObject.offset + 2 > data.length) {
                     throw new NullPointerException (
                       "parseResourceRecord: offset beyond length of data");
                }
                int preference;
                String mailExchanger = "";
                try {
                    preference = getShort(data, offsetObject); 
                    mailExchanger = decodeDomainName(data, offsetObject);
                }
                catch (NullPointerException e) {
                    return null;
                }
                if (mailExchanger == "") {
                    return null;
                }
                String resultMX = "Preference: " + Integer.toString(preference)
                                 + "; Mail Exchanger: " + mailExchanger;
                newRR = new ResourceRecord(domainName, type, ttl, resultMX);
                verbosePrintResourceRecord(newRR, typeCode);              
                return newRR;

           
            case SOA :
                String mName = "", rName = "";
                int serial, refresh, retry, expire, minimum;
                try {
                    mName = decodeDomainName(data, offsetObject);
                    rName = decodeDomainName(data, offsetObject);
                    serial = getInt(data, offsetObject);
                    refresh = getInt(data, offsetObject);
                    retry = getInt(data, offsetObject);
                    expire = getInt(data, offsetObject);
                    minimum = getInt(data, offsetObject);
 
                }
                catch (NullPointerException e) {
                    return null;
                }
                if (mName == "" || rName == "") {
                    return null;
                }
                String resultSOA = "MNAME: " + mName + 
                                 "; RNAME: " + rName + 
                                 "; SERIAL: " + Integer.toString(serial) + 
                                 "; REFRESH: " + Integer.toString(refresh) + 
                                 "; RETRY: " + Integer.toString(retry) + 
                                 "; EXPIRE: " + Integer.toString(expire) + 
                                 "; MINIMUM: " + Integer.toString(minimum); 
                newRR = new ResourceRecord(domainName, type, ttl, resultSOA);
                verbosePrintResourceRecord(newRR, typeCode);              
                return newRR;


            default : 
                String resultOther = parseOtherTypeRR(data, offsetObject, 
                                                      typeCode, dataLength);
                newRR = new ResourceRecord(domainName, type, ttl, resultOther);
                verbosePrintResourceRecord(newRR, typeCode);              
                return newRR; 
       }
    }

 
    /**
     * Get and parse the header of a response datagram.
     *
     * @param response      Response datagram, what we need to parse.
     * 
     * @return The header in DNSHeader class. 
     */
    public static DNSHeader parseHeader(DatagramPacket response) {

        // get whole data
        byte [] data = response.getData();
        // Check if data contains enough length for a header
        if (data.length < 12)
            return null;


        // Parse the header
        try {
            DNSHeader header = new DNSHeader(data);
            //printHeader(header);
            return header;
        }
        catch (Exception e) {
            return null;
        }
    }
 

    /**
     * Parse a response datagram. Check its question section. And parse all RR
     * and add them into cache
     *
     * @param queryNode     DNSNode corresponds to the query we sent.
     * @param response      Response datagram, what we need to parse.
     * @param verbose       If verbose tracing is on.
     * 
     * @return Exception code, 0 if no exception, 
     *                         1 if couldn't parse a record. 
     *                         2 if question section doesn't match our query.
     *                         3 if packet's length less or equal to length of
     *                           a header.                      
     */
    public static int parseResponse( DNSNode queryNode, 
                                     DatagramPacket response,
                                     boolean verbose) {
        verboseTracing = verbose;

        // get whole data
        byte [] data = response.getData();
        // Check if data contains enough length for a header
        if (data.length <= 12)
            return 3;


        // Parse the header
        DNSHeader header = null;
        try {
            header = new DNSHeader(data);
        }
        catch (Exception e) {
            return 1;
        }
//        printHeader(header);
//        // Check exception in header
//        if (header.getTransactionID() != transactionID) return -1;
//        if (header.getRCODE() != 0) return header.getRCODE();
//        if (header.getTC() == true) return -2;
//        if (header.getAA() == true && header.getANCOUNT() == 0) return -3;
        // The packet should be a response for only one standard query
 //       if (header.getQR() != true || 
 //          header.getOPCODE() != DNSHeader.OPCODE_QUERY ||
//            header.getQDCOUNT() != 1)
//            return -9;


        Offset offsetObject = new Offset(12);
        // Read the query
        DNSNode receiveQueryNode = null;
        try {
            receiveQueryNode = parseQuestion(data, offsetObject);
        }
        catch (NullPointerException e) {
            return 1;
        }
        // Check if received question corresponds to ours.
        if (receiveQueryNode == null) {
            return 1;
        }
        else if (receiveQueryNode.equals(queryNode) == false) {
            return 2;
        }

            
        // Read each RR and save in the cache
        int totalRRCount = header.getANCOUNT() + header.getNSCOUNT() + 
                           header.getARCOUNT();
        int indexANStart = 0;
        int indexNSStart = header.getANCOUNT();
        int indexARStart = header.getANCOUNT() + header.getNSCOUNT();

        for(int i = 0; i <= totalRRCount; i++) {
            if (verboseTracing == true) {
                if (i == indexANStart) {
                    System.out.printf("  Answers (%d)\n", header.getANCOUNT());
                }
                if (i == indexNSStart) {
                    System.out.printf("  Nameservers (%d)\n", 
                                       header.getNSCOUNT());
                }
                if (i == indexARStart) {
                    System.out.printf("  Additional Information (%d)\n", 
                                       header.getARCOUNT());
                }
            }           

            if (i == totalRRCount) break;

            ResourceRecord newRecord = null; 
            // Try to read a new record          
            try{
                newRecord = parseResourceRecord(data, offsetObject);
            }
            catch (NullPointerException e) {
                return 1; 
            }
            
            if (newRecord == null) {
                return 1;
            }

            DNSLookupService.addToCache(newRecord);
        }


        return 0;
    }

}
