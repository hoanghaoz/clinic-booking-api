package com.se100.clinic.appointment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.imageio.ImageIO;

final class BookingQr {
  private static final int SIZE = 200;

  private BookingQr() {}

  static String hash(String payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  static String pngBase64(String payload) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, SIZE, SIZE);
      BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_BINARY);
      for (int y = 0; y < SIZE; y++) {
        for (int x = 0; x < SIZE; x++) {
          image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        }
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      ImageIO.write(image, "PNG", buffer);
      return Base64.getEncoder().encodeToString(buffer.toByteArray());
    } catch (WriterException | IOException error) {
      throw new IllegalStateException("Cannot render booking QR", error);
    }
  }
}
