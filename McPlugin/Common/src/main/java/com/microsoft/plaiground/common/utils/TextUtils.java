package com.microsoft.greenlands.common.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

public class TextUtils {

  /**
   * Compress input string with GZip and return base64 encoded string.
   */
  public static String gzipCompressAndBase64Encode(String input) {
    // https://lifelongprogrammer.blogspot.com/2013/11/java-use-zip-stream-and-base64-to-compress-big-string.html
    var byteOutputStream = new ByteArrayOutputStream();
    GZIPOutputStream gzOuputStream;

    try {
      gzOuputStream = new GZIPOutputStream(byteOutputStream);
      gzOuputStream.write(input.getBytes());
      gzOuputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }

    var bytes = byteOutputStream.toByteArray();

    return Base64.getEncoder().encodeToString(bytes);
  }

}
