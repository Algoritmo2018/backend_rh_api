package com.minhaempresa.recrutamentoapi.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small files with real signatures, for upload tests. */
public final class SampleFiles {

    private static final String WORD_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""";
    private static final String MACRO_TYPES = WORD_TYPES.replace("wordprocessingml.document.main+xml", "ms-word.document.macroEnabled.main+xml");

    private SampleFiles() {}

    public static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    /** A PDF of exactly {@code size} bytes. */
    public static byte[] pdf(int size) {
        byte[] content = Arrays.copyOf(pdf(), size);
        Arrays.fill(content, pdf().length, size, (byte) ' ');
        return content;
    }

    public static byte[] png(int size) {
        byte[] content = new byte[size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(signature, 0, content, 0, signature.length);
        return content;
    }

    public static byte[] jpeg(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    public static byte[] docx() {
        return zip(WORD_TYPES, false);
    }

    /** A Word document with a VBA project (macros): never accepted. */
    public static byte[] docxWithMacros() {
        return zip(MACRO_TYPES, true);
    }

    /** Any other ZIP (e.g. renamed to .docx): not a Word document. */
    public static byte[] plainZip() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("hello".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Legacy Word ({@code .doc}, OLE container): refused (decision D6-5). */
    public static byte[] legacyDoc() {
        byte[] content = new byte[512];
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(ole, 0, content, 0, ole.length);
        return content;
    }

    private static byte[] zip(String contentTypes, boolean macros) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml", contentTypes);
            entry(zip, "word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>");
            if (macros) {
                entry(zip, "word/vbaProject.bin", "macro");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
