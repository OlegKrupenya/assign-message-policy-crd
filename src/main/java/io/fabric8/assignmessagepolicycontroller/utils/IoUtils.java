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

            // Get the root element
            Node company = doc.getFirstChild();

            // Get the payload element , it may not working if tag has spaces, or
            // whatever weird characters in front...it's better to use
            // getElementsByTagName() to get it directly.
            // Node payload = company.getFirstChild();

            // Get the payload element by tag name directly
            Node payload = doc.getElementsByTagName("Payload").item(0);

            // update payload attribute
            String data = payload.getTextContent();

            NodeList childNodes = payload.getChildNodes();
            String newContent = """
[{"id": "%s", "description": "%s"}]
""";
            String newPayload = String.format(newContent, spec.getOrderId(), spec.getDescription());
            payload.setTextContent(newPayload);
//            Node nodeAttr = attr.getNamedItem("id");
//            nodeAttr.setTextContent("2");
//
//            // append a new node to payload
//            Element age = doc.createElement("age");
//            age.appendChild(doc.createTextNode("28"));
//            payload.appendChild(age);
//
//            // loop the payload child node
//            NodeList list = payload.getChildNodes();
//
//            for (int i = 0; i < list.getLength(); i++) {
//
//                Node node = list.item(i);
//
//                // get the salary element, and update the value
//                if ("salary".equals(node.getNodeName())) {
//                    node.setTextContent("2000000");
//                }
//
//                //remove firstname
//                if ("firstname".equals(node.getNodeName())) {
//                    payload.removeChild(node);
//                }
//
//            }

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filepath));
            transformer.transform(source, result);

            System.out.println("Done");

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
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
