package io.fabric8.assignmessagepolicycontroller.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class IoUtils {
    public static void copyApiProxyToTarget() {
        String source = "/Users/taipan/dev/sources/apiproxy";
        File srcDir = new File(source);

        String destination = "/Users/taipan/dev/sources/assign-message-policy-crd/target/apiproxy";
        File destDir = new File(destination);

        try {
            FileUtils.copyDirectory(srcDir, destDir);
            zipApiProxy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void zipApiProxy() throws IOException {
        String sourceFile = "/Users/taipan/dev/sources/assign-message-policy-crd/target/apiproxy";
        FileOutputStream fos = new FileOutputStream("/Users/taipan/dev/sources/assign-message-policy-crd/target/apiproxy.zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File fileToZip = new File(sourceFile);

        zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.close();
        fos.close();
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }
}
