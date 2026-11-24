package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.dto.KeadJobPage;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KEAD API XML 응답 파서 (JDK 내장 DOM 사용, 추가 의존성 없음).
 * 바이트로 받아 XML 선언의 인코딩(utf-8)을 파서가 직접 해석하게 한다.
 */
@Component
public class KeadJobXmlParser {

    public KeadJobPage parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 엔티티·DTD 공격 방지
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            Element root = doc.getDocumentElement();

            // 정상 응답: resultCode/resultMsg, 오류 응답(인증 오류 등): returnReasonCode/returnAuthMsg/errMsg
            String resultCode = firstNonNull(text(root, "resultCode"), text(root, "returnReasonCode"));
            String resultMsg = firstNonNull(text(root, "resultMsg"), text(root, "returnAuthMsg"), text(root, "errMsg"));

            List<KeadJobItem> items = new ArrayList<>();
            NodeList itemNodes = root.getElementsByTagName("item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element itemElement = (Element) itemNodes.item(i);
                Map<String, String> fields = new LinkedHashMap<>();
                NodeList children = itemElement.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        fields.put(child.getNodeName(), child.getTextContent());
                    }
                }
                items.add(new KeadJobItem(fields));
            }

            return new KeadJobPage(
                    resultCode,
                    resultMsg,
                    intOrZero(text(root, "totalCount")),
                    intOrZero(text(root, "pageNo")),
                    intOrZero(text(root, "numOfRows")),
                    items);
        } catch (KeadApiException e) {
            throw e;
        } catch (Exception e) {
            // 원문(키가 포함될 수 있는 내용)을 메시지에 넣지 않는다
            throw new KeadApiException("KEAD 응답 XML 파싱 실패: " + e.getClass().getSimpleName());
        }
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int intOrZero(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
