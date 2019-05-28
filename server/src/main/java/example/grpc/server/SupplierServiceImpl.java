package example.grpc.server;

import java.security.Key;
import java.security.MessageDigest;

import java.io.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.util.concurrent.atomic.AtomicInteger;

/* helper to print binary in hexadecimal */
import static javax.xml.bind.DatatypeConverter.printHexBinary;

/* predefined types */
import com.google.type.Money;
import com.google.protobuf.ByteString;

/* these classes are generated from protobuf definitions */
import example.grpc.Product;
import example.grpc.ProductsRequest;
import example.grpc.ProductsResponse;
import example.grpc.SignedResponse;
import example.grpc.Signature;
import example.grpc.SupplierGrpc;

/* grpc library */
import io.grpc.stub.StreamObserver;


public class SupplierServiceImpl extends SupplierGrpc.SupplierImplBase {

  private final AtomicInteger counter = new AtomicInteger(0); 

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

  @Override
  public void listProducts(ProductsRequest request, StreamObserver<SignedResponse> responseObserver) {

    System.out.println("listProducts called");
    System.out.println("Received request:");
    System.out.println("in binary hexadecimals:");
    byte[] requestBinary = request.toByteArray();
    System.out.println(printHexBinary(requestBinary));
    System.out.printf("%d bytes%n", requestBinary.length);

    // build response
    ProductsResponse.Builder responseBuilder = ProductsResponse.newBuilder();
    responseBuilder.setSupplierIdentifier("Tagus Sports Store");
    {
      Product.Builder productBuilder = Product.newBuilder();
      productBuilder.setIdentifier("A1");
      productBuilder.setDescription("Soccer ball");
      productBuilder.setQuantity(22);
      Money.Builder moneyBuilder = Money.newBuilder();
      moneyBuilder.setCurrencyCode("EUR").setUnits(10);
      productBuilder.setPrice(moneyBuilder.build());
      productBuilder.setDiscount(0);
      responseBuilder.addProduct(productBuilder.build());
    }
    {
      Product.Builder productBuilder = Product.newBuilder();
      productBuilder.setIdentifier("B2");
      productBuilder.setDescription("Basketball");
      productBuilder.setQuantity(100);
      Money.Builder moneyBuilder = Money.newBuilder();
      moneyBuilder.setCurrencyCode("EUR").setUnits(12);
      productBuilder.setPrice(moneyBuilder.build());
      productBuilder.setDiscount(0);
      responseBuilder.addProduct(productBuilder.build());
    }
    {
      Product.Builder productBuilder = Product.newBuilder();
      productBuilder.setIdentifier("C3");
      productBuilder.setDescription("Volley ball");
      productBuilder.setQuantity(7);
      Money.Builder moneyBuilder = Money.newBuilder();
      moneyBuilder.setCurrencyCode("EUR").setUnits(8);
      productBuilder.setPrice(moneyBuilder.build());
      productBuilder.setDiscount(0);
      responseBuilder.addProduct(productBuilder.build());
    }

    ProductsResponse products = responseBuilder.build();

    // build signature
    Signature.Builder signatureBuilder = Signature.newBuilder();
    signatureBuilder.setSignerId("Tagus Sports Store");

    // build digest
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(products.toByteArray());
      byte[] digest = messageDigest.digest();

      final String SYM_CIPHER = "AES/ECB/PKCS5Padding";
      Cipher cipher = Cipher.getInstance(SYM_CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, readKey("secret.key"));

      signatureBuilder.setCounter(counter.getAndIncrement());
      signatureBuilder.setValue(ByteString.copyFrom(cipher.doFinal(digest)));

    } catch (Exception e) {
      System.err.println("Unable to digest or cipher data");
    }

    // build signed response
    SignedResponse.Builder signedResponse = SignedResponse.newBuilder();
    signedResponse.setResponse(products);
    signedResponse.setSignature(signatureBuilder.build());

    SignedResponse response = signedResponse.build(); 

    System.out.println("Response to send:");
    System.out.println(response);
    System.out.println("in binary hexadecimals:");
    byte[] responseBinary = response.toByteArray();
    System.out.println(printHexBinary(responseBinary));
    System.out.printf("%d bytes%n", responseBinary.length);

    // send single response back
    responseObserver.onNext(response);
    // complete call
    responseObserver.onCompleted();
  }
}
