package example.grpc.client;

import java.security.Key;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.io.InputStream;

import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* helper to print binary in hexadecimal */
import static javax.xml.bind.DatatypeConverter.printHexBinary;

/* classes are generated from protobuf definitions */
import example.grpc.ProductsRequest;
import example.grpc.ProductsResponse;
import example.grpc.SignedResponse;
import example.grpc.Signature;
import example.grpc.SupplierGrpc;

/* grpc library */
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;


public class SupplierClient {

  private static Map<String, Long> signerCounter = new ConcurrentHashMap<>();

  public static SecretKeySpec readKey(String resourcePath) throws Exception {
    System.out.println("Reading key from resource " + resourcePath + " ...");

    InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
    byte[] encoded = new byte[fis.available()];
    fis.read(encoded);
    fis.close();

    System.out.println("Key:");
    System.out.println(printHexBinary(encoded));
    SecretKeySpec keySpec = new SecretKeySpec(encoded, "AES");

    return keySpec;

  }  

  public static void main(String[] args) throws Exception {
    System.out.println(SupplierClient.class.getSimpleName() + " starting ...");

    // receive and print arguments
    System.out.printf("Received %d arguments%n", args.length);
    for (int i = 0; i < args.length; i++) {
      System.out.printf("arg[%d] = %s%n", i, args[i]);
    }

    // check arguments
    if (args.length < 2) {
      System.err.println("Argument(s) missing!");
      System.err.printf("Usage: java %s host port%n", SupplierClient.class.getName());
      return;
    }

    final String host = args[0];
    final int port = Integer.parseInt(args[1]);
    final String target = host + ":" + port;

    // Channel is the abstraction to connect to a service endpoint
    final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

    // create a blocking stub for making synchronous remote calls
    SupplierGrpc.SupplierBlockingStub stub = SupplierGrpc.newBlockingStub(channel);

    // prepare request
    ProductsRequest request = ProductsRequest.newBuilder().build();
    System.out.println("Request to send:");
    System.out.println(request);
    System.out.println("in binary hexadecimals:");
    byte[] requestBinary = request.toByteArray();
    System.out.println(printHexBinary(requestBinary));
    System.out.printf("%d bytes%n", requestBinary.length);

    // make the call using the stub
    System.out.printf("%nRemote call...%n%n");
    for (int i = 0; i <= 1; i++) {
      SignedResponse response = stub.listProducts(request);

      // validate counter
      System.out.println("[Crypto] validating counter...");
      String signerId = response.getSignature().getSignerId();
      if (null == signerCounter.get(signerId)) {
        signerCounter.put(signerId, response.getSignature().getCounter()); 
      } else {
        if (response.getSignature().getCounter() <= signerCounter.get(signerId)) {
          System.out.println("[Crypto] receiving old message");
          return;
        } 
        System.out.println("[Crypto] counter is valid");
        signerCounter.put(signerId, response.getSignature().getCounter());
      }

      // validate digest
      System.out.println("[Crypto] deciphering digest in signature...");
      final String SYM_CIPHER = "AES/ECB/PKCS5Padding";
      Cipher cipher = Cipher.getInstance(SYM_CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, readKey("secret.key"));
      byte[] decipheredDigest = cipher.doFinal(response.getSignature().getValue().toByteArray());

      System.out.println("[Crypto] calculating digest in response...");
      MessageDigest messageDigest =  MessageDigest.getInstance("SHA-256"); 
      messageDigest.update(response.getResponse().toByteArray());
      byte[] digest = messageDigest.digest();

      System.out.println("[Crypto] asserting message validity...");
      if (java.util.Arrays.equals(digest, decipheredDigest)) {
        System.out.println("[Crypto] Signature is valid! Message accepted!");
        // print response
        System.out.println("Received response:");
        System.out.println(response);
        System.out.println("in binary hexadecimals:");
        byte[] responseBinary = response.toByteArray();
        System.out.println(printHexBinary(responseBinary));
        System.out.printf("%d bytes%n", responseBinary.length);
      } else {
        System.out.println("[Crypto] Signature is invalid! Message rejected!");
      }
    }
    // A Channel should be shutdown before stopping the process.
    channel.shutdownNow();
  }

}
