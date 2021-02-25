package io.fabric8.assignmessagepolicycontroller.utils;

import io.fabric8.assignmessagepolicycontroller.api.model.v1alpha1.AssignMessagePolicySpec;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class IoUtils {
    public static void copyApiProxyToTarget(AssignMessagePolicySpec spec) {
        String source = "/Users/taipan/dev/sources/apiproxy";
        File srcDir = new File(source);

        String destination = "/Users/taipan/dev/sources/assign-message-policy-crd/target/apiproxy";
        File destDir = new File(destination);

        try {
            FileUtils.copyDirectory(srcDir, destDir);
            createAssignMessagePolicyFromSpec(spec, destination);
            zipApiProxy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createAssignMessagePolicyFromSpec(AssignMessagePolicySpec spec, String destination) {
        try {
            String filepath = destination + "/policies/AM-CreateResponseMsg.xml";
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(filepath);

            Node payload = doc.getElementsByTagName("Payload").item(0);

            String newContent = """
[{"id": "%s", "description": "%s"}]
""";
            String newPayload = String.format(newContent, spec.getOrderId(), spec.getDescription());
            payload.setTextContent(newPayload);

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filepath));
            transformer.transform(source, result);
        } catch (ParserConfigurationException | TransformerException | IOException | SAXException pce) {
            pce.printStackTrace();
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
