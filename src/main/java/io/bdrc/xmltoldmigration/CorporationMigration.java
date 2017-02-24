package io.bdrc.xmltoldmigration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


public class CorporationMigration {
	
	private static final String CRP = CommonMigration.CORPORATION_PREFIX;
	private static final String PP = CommonMigration.PERSON_PREFIX;
	private static final String PLP = CommonMigration.PLACE_PREFIX;
	private static final String CXSDNS = "http://www.tbrc.org/models/corporation#";
	
	public static Model MigrateCorporation(Document xmlDocument) {
		Model m = ModelFactory.createDefaultModel();
		CommonMigration.setPrefixes(m);
		Element root = xmlDocument.getDocumentElement();
		Element current;
		Resource main = m.createResource(CRP + root.getAttribute("RID"));
		m.add(main, RDF.type, m.createResource(CRP + "Corporation"));
		Property prop = m.getProperty(CRP, "status");
		m.add(main, prop, root.getAttribute("status"));
		
		CommonMigration.addNames(m, root, main, CXSDNS);
		
		CommonMigration.addNotes(m, root, main, CXSDNS);
		
		CommonMigration.addExternals(m, root, main, CXSDNS);
		
		CommonMigration.addLog(m, root, main, CXSDNS);
		
		CommonMigration.addDescriptions(m, root, main, CXSDNS, true);
		
		// members
		
		NodeList nodeList = root.getElementsByTagNameNS(CXSDNS, "member");
		String value = null;
		for (int i = 0; i < nodeList.getLength(); i++) {
			current = (Element) nodeList.item(i);
			value = current.getAttribute("person");
			Resource person = m.createResource(PP + value);
			value = current.getAttribute("type");
			value = CommonMigration.normalizePropName(value, null);
			if (value.isEmpty()) {
				value = "member";
			}
			prop = m.getProperty(CRP+value);
			m.add(main, prop, person);
		}
		
		// regions (ignoring most attributes)
		
		nodeList = root.getElementsByTagNameNS(CXSDNS, "region");
		for (int i = 0; i < nodeList.getLength(); i++) {
			current = (Element) nodeList.item(i);
			value = current.getAttribute("place");
			if (!value.isEmpty()) {
				Resource place = m.createResource(PLP + value);
				prop = m.getProperty(CRP+"region");
				m.add(main, prop, place);
			}
		}
		
		return m;
	}
	
}
