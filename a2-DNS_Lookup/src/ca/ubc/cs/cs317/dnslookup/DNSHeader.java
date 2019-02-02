package ca.ubc.cs.cs317.dnslookup;

import java.lang.NullPointerException;
/** Used to parse and store information in a DNS packet's header
*/
public class DNSHeader {
    // possible value for OPCODE field of DNS header
    static public byte OPCODE_QUERY = 0;
    static public byte OPCODE_IQUERY = 1;
    static public byte OPCODE_STATUS = 2;

    // possible value for RCODE field of DNS header
    static public byte RCODE__NO_ERROR = 0;
    static public byte RCODE_FORMAT_ERROR = 1;
    static public byte RCODE_SERVER_FAILURE = 2;
    static public byte RCODE_NAME_ERROR = 3;
    static public byte RCODE_NOT_IMPLEMENTED = 4;
    static public byte RCODE_REFUSED = 5;

    private short ID;    // ID field in DNS header.
    private boolean QR;  // QR field in DNS header, 0 for query, 1 for response
    private byte OPCODE; // OPCODE field in DNS header;
    private boolean AA;  // AA field in DNS header, 1 if responding server is
                         // authority for the domain name in question section
    private boolean TC;  // TC field in DNS header, 1 if message was truncated
    private boolean RD;  // RD field in DNS header, 1 if resursion desired
    private boolean RA;  // RA field in DNS header, 1 if recursive query is 
                         // available in the name server
    private byte RCODE;  // RCODE field in DNS header 
    private short QDCOUNT; // how many queries in the packet
    private short ANCOUNT; // how many answers in the packet
    private short NSCOUNT; // how many authority records in the packet
    private short ARCOUNT; // how many additional records in the packet

    /**
     * Read a short integer.
     *
     * @param data   Arrays of bytes of data.
     * @param offset Where the short integer begins.
     *
     * @return Return the short integer
     */
    private static short getShort(byte [] data, int offset) {
        short ret = (short) (((data[offset] & 0xFF) << 8) + 
                              (data[offset + 1] & 0xFF));
        return ret;
    }

    /** Construct a DNSHeader instance with 4 bytes.
     *  @param headerByte 4-byte-long byte array corresponds to header
     */
    public DNSHeader(byte [] headerByte) {
        if (headerByte.length < 12)
            throw new NullPointerException("Constructing DNSHeader");
        // read transaction ID
        ID = (short) ((((int)headerByte[0]&0xFF) << 8) + 
             ((int)headerByte[1]&0xFF));
        // read QR
        QR = (headerByte[2] & 0x80) == 0 ? false : true;
        // read OPCODE
        OPCODE = (byte)(((int)headerByte[2]& 0x78) >> 3);
        // read AA
        AA = (headerByte[2] & 0x04) == 0 ? false: true;
        // read TC
        TC = (headerByte[2] & 0x02) == 0 ? false: true;
        // read RD
        RD = (headerByte[2] & 0x01) == 0 ? false: true;
        // read RA
        RA = (headerByte[3] & 0x80) == 0 ? false: true;
        // read RCODE
        RCODE = (byte) ((int)headerByte[3] & 0x0F);
        // read QDCOUNT
        QDCOUNT = getShort(headerByte, 4); 
        // read ANCOUNT
        ANCOUNT = getShort(headerByte, 6); 
        // read NSCOUNT
        NSCOUNT = getShort(headerByte, 8);
        // read ARCOUNT
        ARCOUNT = getShort(headerByte, 10);

    }

    /** Return transaction ID of the header.
      */
    public short getTransactionID() {
        return ID;
    }

    /** Return QR field of the header.
      */
    public boolean getQR() {
        return QR;
    }

    /** Return OPCODE field of the header.
      */
    public byte getOPCODE() {
        return OPCODE;
    }

    /** Return AA field of the header.
      */
    public boolean getAA() {
        return AA;
    }

    /** Return TC field of the header.
      */
    public boolean getTC() {
        return TC;
    }

    /** Return RD field of the header.
      */
    public boolean getRD() {
        return RD;
    }

    /** Return RA field of the header.
      */
    public boolean getRA() {
        return RA;
    }

    /** Return RCODE field of the header.
      */
    public byte getRCODE() {
        return RCODE;
    }

    /** Return QDCOUNT field of the header.
      */
    public short getQDCOUNT() {
        return QDCOUNT;
    }

    /** Return ANCOUNT field of the header.
      */
    public short getANCOUNT() {
        return ANCOUNT;
    }

    /** Return NSCOUNT field of the header.
      */
    public short getNSCOUNT() {
        return NSCOUNT;
    }

    /** Return ARCOUNT field of the header.
      */
    public short getARCOUNT() {
        return ARCOUNT;
    }

}
