package vn.yang.slots;

import java.net.*;

public final class HealthProbe {
  public static void main(String[] args) {
    try {
      String value = System.getenv("PORT");
      int port = value == null ? 3150 : Integer.parseInt(value);
      if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
      HttpURLConnection connection =
          (HttpURLConnection)
              URI.create("http://127.0.0.1:" + port + "/health").toURL().openConnection();
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(2000);
      int code = connection.getResponseCode();
      connection.disconnect();
      System.exit(code == 200 ? 0 : 1);
    } catch (Exception e) {
      System.exit(1);
    }
  }
}
