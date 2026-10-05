package ru.hvostid.passport;

public final class TestDocumentContent {
    private TestDocumentContent() {}

    public static byte[] image(String format) {
        try {
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(
                    new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static byte[] pdf() {
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
                var out = new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
