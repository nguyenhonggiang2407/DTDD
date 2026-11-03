package vn.yang.slots;

import java.net.*;

public final class HealthProbe {
  public static void main(String[] args) {
    try {
      HttpURLConnection connection =
          (HttpURLConnection) URI.create("http://127.0.0.1:3150/health").toURL().openConnection();
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
