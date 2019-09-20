package io.bdrc.xmltoldmigration;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;
import static io.bdrc.libraries.Models.addReleased;
import static io.bdrc.libraries.Models.createAdminRoot;
import static io.bdrc.libraries.Models.createRoot;
import static io.bdrc.libraries.Models.getFacetNode;
import static io.bdrc.libraries.Models.setPrefixes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import io.bdrc.libraries.Models.FacetType;
import io.bdrc.xmltoldmigration.helpers.SymetricNormalization;
import io.bdrc.xmltoldmigration.xml2files.CommonMigration;

public class GRETILTransfer {

    public static final String ORIG_URL_BASE = "http://gretil.sub.uni-goettingen.de/gretil.html";

    public static final void transferGRETIL() {
        System.out.println("Transfering GRETIL works");
        SymetricNormalization.reinit();
        final ClassLoader classLoader = MigrationHelpers.class.getClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream("gretil.csv");
        final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        final CSVReader reader;
        final CSVParser parser = new CSVParserBuilder().build();
        reader = new CSVReaderBuilder(in)
                .withCSVParser(parser)
                .build();
        ArrayList<String> processed=new ArrayList<>();
        try {
            String[] line= reader.readNext();// skip first two lines
            line= reader.readNext();
            line= reader.readNext();
            while (line != null) {
                //avoiding identical originalRecords
                if(line[8]!=null && !processed.contains(line[8])) {
                    Resource work = getWorkFromLine(line);
                    writeGRETILFiles(work);
                    processed.add(line[8]);
                }
                line = reader.readNext();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        MigrationApp.insertMissingSymetricTriples("work");
    }

    public static final void writeGRETILFiles(Resource work) {
        final String workOutfileName = MigrationApp.getDstFileName("work", work.getLocalName());
        MigrationHelpers.outputOneModel(work.getModel(), work.getLocalName(), workOutfileName, "work");
    }
    
    public static final Resource getWorkFromLine(String[] line) {        
        // Work model
        final Model workModel = ModelFactory.createDefaultModel();
        setPrefixes(workModel);
        Resource work = createRoot(workModel, BDR+line[0], BDO+"UnicodeWork");
        Resource admWork = createAdminRoot(work);

        // Work AdminData
        addReleased(workModel, admWork);
        workModel.add(admWork, workModel.createProperty(ADM, "metadataLegal"), workModel.createResource(BDA + "LD_GRETIL")); // ?
        if (line[8] != null && !"".equals(line[8])) {
            final String origUrl = ORIG_URL_BASE+line[8].replace('/', '-');
            workModel.add(admWork, workModel.createProperty(ADM, "originalRecord"), workModel.createTypedLiteral(origUrl, XSDDatatype.XSDanyURI));
        }
        
        // titles
        work.addProperty(SKOS.prefLabel, workModel.createLiteral(line[1], "en"));
        work.addProperty(SKOS.prefLabel, workModel.createLiteral(line[3], "sa-x-iast"));
        Resource titleType = workModel.createResource(BDO+"WorkBibliographicalTitle");
        Resource titleR = getFacetNode(FacetType.TITLE, work, titleType);
        work.addProperty(workModel.createProperty(BDO, "workTitle"), titleR);
        titleR.addProperty(RDFS.label, workModel.createLiteral(line[3], "sa-x-iast"));

        // rKTs metadata
        String rkts=line[2];
        if(rkts!=null) {
            if(rkts.contains(",")) {
                rkts=rkts.substring(0,rkts.indexOf(","));
            }
            final String abstractWorkRID = EAPTransfer.rKTsToBDR(line[2]);
            if (abstractWorkRID != null) {
                SymetricNormalization.addSymetricProperty(workModel, "workExpressionOf", line[0], abstractWorkRID, null);
            }
        }
        
        // creator
        String author=line[5];
        if(author!=null && !"".equals(author)) {
            CommonMigration.addAgentAsCreator(work, workModel.createResource(BDR+author), "hasMainAuthor");
        }
        
        // subject
        String topic=line[6];
        if(topic!=null && !"".equals(topic)) {
            // Basic cchecking but some validation of the topic should occur here
            //We might need to query ldspdi for the list of all topics
            if(topic.startsWith("T")) {
                workModel.add(work, workModel.createProperty(BDO, "workIsAbout"), workModel.createResource(BDR+topic));
            }
        }
        
        // notes
        String note=line[9];
        if (note != null && !note.isEmpty()) {
            CommonMigration.addNote(work, "Input by "+note, "en", null, null);
        }
        note=line[10];
        if (note != null && !note.isEmpty()) {
            CommonMigration.addNote(work, "Based on "+note, "en", null, null);
        }
        
        return work;
    }

}
