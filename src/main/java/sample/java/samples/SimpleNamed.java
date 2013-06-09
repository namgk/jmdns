/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package sample.java.samples;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.impl.DNSIncoming;
import javax.jmdns.impl.DNSOutgoing;
import javax.jmdns.impl.DNSQuestion;
import javax.jmdns.impl.DNSRecord;
import javax.jmdns.impl.constants.DNSConstants;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;

/**
 *
 * @author Nam Giang <zang at kaist dot ac dot kr>
 *
 */
public class SimpleNamed {

  static final String AUTHORITATIVE_DOMAIN = "com.";
  static final String AUTHORITATIVE_NAME_SERVER = "ns.com.";
  static final String AUTHORITATIVE_NAME_SERVER_ADDR = "143.248.56.162";
  static final String AUTHORITATIVE_NAME_SERVER_ADDR6 = "2002:8ff8:38a2::8ff8:38a2";
  static final String AUTHORITATIVE_NAME_REVERSE = "143.in-addr.arpa.";
  static final String AUTHORITATIVE_NAME_ADDR_REVERSE = "162.248.56.143.in-addr.arpa.";
  static final int AUTHORITATIVE_STACK = 46;

  public static void main(String[] args) {
    SimpleNamed sn = new SimpleNamed();

  }

  public SimpleNamed() {
    UDPListener ul = new UDPListener(this);
    ul.start();
  }

  private class UDPListener extends Thread {

    SimpleNamed sn;
    List addresses = new ArrayList();
    DatagramSocket sock;// = new DatagramSocket(port, addr);
    public static final int IPv4 = 1;
    public static final int IPv6 = 2;
    Logger logger = Logger.getLogger(UDPListener.class.getName());

    public UDPListener(SimpleNamed sn) {
      try {
        this.sn = sn;

        sock = new DatagramSocket(53, getByAddress("0.0.0.0"));

      } catch (SocketException ex) {
        Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    @Override
    public void run() {
      byte buf[] = new byte[DNSConstants.MAX_MSG_ABSOLUTE];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      while (true) {
        packet.setLength(buf.length);
        try {
          this.sock.receive(packet);// blocked
          DNSIncoming msg = new DNSIncoming(packet);
          System.out.println(msg.print(true));
          if (msg.isQuery()) {
            handleQuery(msg);
          }
        } catch (IOException ex) {
          Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    }

    private void handleQuery(DNSIncoming msg) {

      ArrayList<DNSRecord> ansRcrdList = new ArrayList<DNSRecord>();
      ArrayList<DNSRecord> authRcrdList = new ArrayList<DNSRecord>();
      ArrayList<DNSRecord> addtnRcrdList = new ArrayList<DNSRecord>();

      DNSOutgoing out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_AA, false, msg.getSenderUDPPayload());

      Collection<? extends DNSQuestion> questions = msg.getQuestions();
      Iterator i = questions.iterator();
      while (i.hasNext()) {
        DNSQuestion aQuestion = (DNSQuestion) i.next();
        String requestedName = aQuestion.getName();

        if (aQuestion.getRecordType() == DNSRecordType.TYPE_PTR) {
          ansRcrdList.add(new DNSRecord.Pointer(AUTHORITATIVE_NAME_ADDR_REVERSE, DNSRecordClass.CLASS_IN, true, DNSConstants.DNS_TTL, AUTHORITATIVE_DOMAIN));
          authRcrdList.add(createAuthRecord(true));
          break;
        } else {
          if (!((requestedName.endsWith("." + AUTHORITATIVE_DOMAIN)) || (requestedName.equals(AUTHORITATIVE_DOMAIN)))) {
            out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_RF, false, msg.getSenderUDPPayload());
            break;
          }

          DNSRecord ansRcrd = createAnsRecord(requestedName, (aQuestion.getRecordType() == DNSRecordType.TYPE_A) ? false : true);
          if (ansRcrd != null) {
            ansRcrdList.add(ansRcrd);
          }
          authRcrdList.add(createAuthRecord(false));
        }

        // Attach questions to response
        try {
          out.addQuestion(aQuestion);
        } catch (IOException ex) {
          Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
        }
      }

      if (!out.isRefused()) {
        // Acceptable, in-domain request
        if (ansRcrdList.isEmpty()) {
          // Not exist
          out = new DNSOutgoing(DNSConstants.FLAGS_QR_RESPONSE | DNSConstants.FLAGS_NE, false, msg.getSenderUDPPayload());
        } else {
          addtnRcrdList.add(createAddtnRecord(AUTHORITATIVE_NAME_SERVER, AUTHORITATIVE_NAME_SERVER_ADDR, false));
          addtnRcrdList.add(createAddtnRecord(AUTHORITATIVE_NAME_SERVER, AUTHORITATIVE_NAME_SERVER_ADDR6, true));

          try {
            for (DNSRecord r : ansRcrdList) {
              out.addAnswer(msg, r);
            }
            for (DNSRecord r : authRcrdList) {
              out.addAuthorativeAnswer(r);
            }
            for (DNSRecord r : addtnRcrdList) {
              out.addAdditionalAnswer(msg, r);
            }
          } catch (IOException ex) {
            Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
      }
      
      out.setId(msg.getId());
      byte[] message = out.data();
      DatagramPacket packet = new DatagramPacket(message, message.length, msg.getPacket().getAddress(), msg.getPacket().getPort());
      try {
        sock.send(packet);
      } catch (IOException ex) {
        Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    public DNSRecord createAnsRecord(String requestedName, boolean ipv6) {
      DNSRecord result = null;
      String resolvedAddr = null;
      String resolvedAddr6 = null;
      // look up
      if (requestedName.equals("facebook.com.")) {
        resolvedAddr = "77.77.77.77";
        resolvedAddr6 = "2002:8ff8:3853::7777:7777";
      } else if (requestedName.equals("google.com.")) {
        resolvedAddr = "90.99.1.3";
        resolvedAddr6 = "2002:8ff8:3853::9009:9999";
      } else if (requestedName.equals("ipv6.com.")) {
        resolvedAddr6 = "2002:8ff8:3853::1426:1426";
      }
      if ((!ipv6) && (resolvedAddr != null)) {
        result = new DNSRecord.IPv4Address(requestedName, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, getByAddress(resolvedAddr));
      } else if (ipv6 && (resolvedAddr6 != null)) {
        result = new DNSRecord.IPv6Address(requestedName, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, getByAddress(resolvedAddr6));
      }

      return result;

    }

    public DNSRecord createAuthRecord(boolean reverse) {
      if (reverse) {
        return new DNSRecord.Authoritative(AUTHORITATIVE_NAME_REVERSE, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, AUTHORITATIVE_NAME_SERVER);
      }
      return new DNSRecord.Authoritative(AUTHORITATIVE_DOMAIN, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, AUTHORITATIVE_NAME_SERVER);
    }

    public DNSRecord createAddtnRecord(String serverName, String serverAddr, boolean ipv6) {
      if (!ipv6) {
        return new DNSRecord.IPv4Address(serverName, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, getByAddress(serverAddr));
      }
      return new DNSRecord.IPv6Address(serverName, DNSRecordClass.CLASS_IN, DNSRecordClass.UNIQUE, DNSConstants.DNS_TTL, getByAddress(serverAddr));
    }

    public InetAddress getByAddress(String addr) {
      try {

        byte[] bytes;
        bytes = toByteArray(addr, IPv4);
        if (bytes != null) {

          return InetAddress.getByAddress(addr, bytes);

        }
        bytes = toByteArray(addr, IPv6);
        if (bytes != null) {
          return InetAddress.getByAddress(addr, bytes);
        }
        throw new UnknownHostException("Invalid address: " + addr);

      } catch (UnknownHostException ex) {
        Logger.getLogger(SimpleNamed.class.getName()).log(Level.SEVERE, null, ex);
        return null;
      }
    }

    public byte[] toByteArray(String s, int family) {
      if (family == IPv4) {
        return parseV4(s);
      } else if (family == IPv6) {
        return parseV6(s);
      } else {
        throw new IllegalArgumentException("unknown address family");
      }
    }

    private byte[] parseV4(String s) {
      int numDigits;
      int currentOctet;
      byte[] values = new byte[4];
      int currentValue;
      int length = s.length();

      currentOctet = 0;
      currentValue = 0;
      numDigits = 0;
      for (int i = 0; i < length; i++) {
        char c = s.charAt(i);
        if (c >= '0' && c <= '9') {
          /*
           * Can't have more than 3 digits per octet.
           */
          if (numDigits == 3) {
            return null;
          }
          /*
           * Octets shouldn't start with 0, unless they are 0.
           */
          if (numDigits > 0 && currentValue == 0) {
            return null;
          }
          numDigits++;
          currentValue *= 10;
          currentValue += (c - '0');
          /*
           * 255 is the maximum value for an octet.
           */
          if (currentValue > 255) {
            return null;
          }
        } else if (c == '.') {
          /*
           * Can't have more than 3 dots.
           */
          if (currentOctet == 3) {
            return null;
          }
          /*
           * Two consecutive dots are bad.
           */
          if (numDigits == 0) {
            return null;
          }
          values[currentOctet++] = (byte) currentValue;
          currentValue = 0;
          numDigits = 0;
        } else {
          return null;
        }
      }
      /*
       * Must have 4 octets.
       */
      if (currentOctet != 3) {
        return null;
      }
      /*
       * The fourth octet can't be empty.
       */
      if (numDigits == 0) {
        return null;
      }
      values[currentOctet] = (byte) currentValue;
      return values;
    }

    private byte[] parseV6(String s) {
      int range = -1;
      byte[] data = new byte[16];

      String[] tokens = s.split(":", -1);

      int first = 0;
      int last = tokens.length - 1;

      if (tokens[0].length() == 0) {
        // If the first two tokens are empty, it means the string
        // started with ::, which is fine.  If only the first is
        // empty, the string started with :, which is bad.
        if (last - first > 0 && tokens[1].length() == 0) {
          first++;
        } else {
          return null;
        }
      }

      if (tokens[last].length() == 0) {
        // If the last two tokens are empty, it means the string
        // ended with ::, which is fine.  If only the last is
        // empty, the string ended with :, which is bad.
        if (last - first > 0 && tokens[last - 1].length() == 0) {
          last--;
        } else {
          return null;
        }
      }

      if (last - first + 1 > 8) {
        return null;
      }

      int i, j;
      for (i = first, j = 0; i <= last; i++) {
        if (tokens[i].length() == 0) {
          if (range >= 0) {
            return null;
          }
          range = j;
          continue;
        }

        if (tokens[i].indexOf('.') >= 0) {
          // An IPv4 address must be the last component
          if (i < last) {
            return null;
          }
          // There can't have been more than 6 components.
          if (i > 6) {
            return null;
          }
          byte[] v4addr = toByteArray(tokens[i], IPv4);
          if (v4addr == null) {
            return null;
          }
          for (int k = 0; k < 4; k++) {
            data[j++] = v4addr[k];
          }
          break;
        }

        try {
          for (int k = 0; k < tokens[i].length(); k++) {
            char c = tokens[i].charAt(k);
            if (Character.digit(c, 16) < 0) {
              return null;
            }
          }
          int x = Integer.parseInt(tokens[i], 16);
          if (x > 0xFFFF || x < 0) {
            return null;
          }
          data[j++] = (byte) (x >>> 8);
          data[j++] = (byte) (x & 0xFF);
        } catch (NumberFormatException e) {
          return null;
        }
      }

      if (j < 16 && range < 0) {
        return null;
      }

      if (range >= 0) {
        int empty = 16 - j;
        System.arraycopy(data, range, data, range + empty, j - range);
        for (i = range; i < range + empty; i++) {
          data[i] = 0;
        }
      }

      return data;
    }
  }
}
