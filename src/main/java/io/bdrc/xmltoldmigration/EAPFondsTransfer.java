package io.bdrc.xmltoldmigration;

import static io.bdrc.libraries.Models.ADM;
import static io.bdrc.libraries.Models.BDA;
import static io.bdrc.libraries.Models.BDO;
import static io.bdrc.libraries.Models.BDR;
import static io.bdrc.libraries.Models.FacetType;
import static io.bdrc.libraries.Models.createAdminRoot;
import static io.bdrc.libraries.Models.createRoot;
import static io.bdrc.libraries.Models.getAdminData;
import static io.bdrc.libraries.Models.getFacetNode;
import static io.bdrc.libraries.Models.setPrefixes;
import org.apache.jena.rdf.model.Literal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

public class EAPFondsTransfer {

    public HashMap<String,HashMap<String,String[]>> seriesByCollections;
    public HashMap<String,String[]> seriesLines;
    public List<String[]> lines;
    public boolean simplified; // for the eap2.csv file that contains less columns
    
    private static final String ManifestPREFIX = "https://eap.bl.uk/archive-file/";
    public static final String ORIG_URL_BASE = "https://eap.bl.uk/collection/";

    public EAPFondsTransfer(String filename, boolean simplified) throws IOException {
        this.simplified = simplified;
        CSVReader reader;
        CSVParser parser = new CSVParserBuilder().build();
        InputStream inputStream = EAPFondsTransfer.class.getClassLoader().getResourceAsStream(filename);
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        reader = new CSVReaderBuilder(in)
                .withCSVParser(parser)
                .build();
        lines=reader.readAll();
        getSeriesByFonds();
    }

    public void getSeriesByFonds() throws IOException{
        seriesByCollections = new HashMap<>();
        for (String[] line:lines) {
            String type = this.simplified ? line[0] : line[1];
            if (type.toLowerCase().equals("fonds")) {
                seriesByCollections.put(simplified ? line[1] : line[0], new HashMap<>());
            }
        }
        for(String s : seriesByCollections.keySet()) {
            HashMap<String,String[]> mp = seriesByCollections.get(s);
            for(String[] line:lines) {
                if ((!simplified && line[3].equals(s)) || simplified && line[0].toLowerCase().startsWith("serie") && line[1].startsWith(s+"/")) {
                    mp.put(line[0], line);
                }
            }
            seriesByCollections.put(s,mp);
        }
    }

    public List<String[]> getVolumes(String serie){
        List<String[]> volumes = new ArrayList<>();
        for(String[] line:lines) {
            if ((!simplified && line[3].equals(serie)) || simplified && line[0].toLowerCase().startsWith("file") && line[1].startsWith(serie+"/")) {
                volumes.add(line);
            }
        }
        return volumes;
    }
    
    public final void writeEAPFiles(List<Resource> resources) {
        int nbresources = 0;
        for(Resource r: resources) {
            String uri=r.getProperty(RDF.type).getObject().asResource().getLocalName();
            //r.getModel().write(System.out, "TURTLE");
            nbresources += 1;
            switch(uri) {
                case "Work":
                    final String workOutfileName = MigrationApp.getDstFileName("work", r.getLocalName());
                    MigrationHelpers.outputOneModel(r.getModel(), r.getLocalName(), workOutfileName, "work");
                    break;
                case "ItemImageAsset":
                case "Item":
                    final String itemOutfileName = MigrationApp.getDstFileName("item", r.getLocalName());
                    MigrationHelpers.outputOneModel(r.getModel(), r.getLocalName(), itemOutfileName, "item");
                    break;
            }
        }
        System.out.println("wrote "+nbresources+" files of eap funds");
    }

    public Literal getLiteral(String title, Model m) {
        int firstChar = title.codePointAt(0);
        String lang = "bo-x-ewts";
        if (firstChar > 3840 && firstChar < 4095) {
            lang = "bo";
        }
        if (title.endsWith("@en")) {
            title = title.substring(0, title.length()-3);
            lang = "en";
        }
        return m.createLiteral(title, lang);
    }
    
    public Integer getVolNum(String[] line) {
        if (simplified) {
            String id = line[1];
            int lastSlashIdx = id.lastIndexOf('/');
            return Integer.parseInt(id.substring(lastSlashIdx+1));
        } else {
            return Integer.parseInt(line[37]);
        }
    }
    
    public List<Resource> getResources(){
        Set<String> keys=seriesByCollections.keySet();
        List<Resource> res = new ArrayList<>();
        for(String key:keys) {
            HashMap<String,String[]> map=seriesByCollections.get(key);
            Set<String> seriesKeys=map.keySet();
            for(String serie:seriesKeys) {
                String[] serieLine = seriesByCollections.get(key).get(serie);
                String serieID = (simplified ? serieLine[1] : serieLine[4]).replace('/', '-');
                                
                // Work model
                Model workModel = ModelFactory.createDefaultModel();
                setPrefixes(workModel);
                Resource work = createRoot(workModel, BDR+"W"+serieID, BDO+"Work");
                Resource admWork = createAdminRoot(work);
                res.add(work);

                // Work adm:AdminData
                Resource ldEAP = workModel.createResource(BDA+"LD_EAP");
                workModel.add(admWork, RDF.type, workModel.createResource(ADM+"AdminData"));
                workModel.add(admWork, workModel.getProperty(ADM+"status"), workModel.createResource(BDR+"StatusReleased"));
                workModel.add(admWork, workModel.createProperty(ADM, "metadataLegal"), ldEAP); // ?
                String origUrl = ORIG_URL_BASE+serieID;
                workModel.add(admWork, workModel.createProperty(ADM, "originalRecord"), workModel.createTypedLiteral(origUrl, XSDDatatype.XSDanyURI));                
                
                // bdo:Work
                workModel.add(work, workModel.createProperty(BDO, "workLangScript"), workModel.createResource(BDR+"BoTibt"));
                String noteText;
                if (simplified) {
                    noteText = serieLine[10]+serieLine[11]+serieLine[12];
                } else {
                    noteText = serieLine[36];
                }
                if (!noteText.isEmpty()) {
                    Resource noteR = getFacetNode(FacetType.NOTE,  work);
                    noteR.addLiteral(workModel.createProperty(BDO, "noteText"), workModel.createLiteral(noteText,"en"));
                    workModel.add(work, workModel.createProperty(BDO, "note"), noteR);
                }
                workModel.add(work, SKOS.prefLabel, getLiteral(simplified ? serieLine[9] : serieLine[39], workModel));
                String notBefore = simplified ? serieLine[3] : serieLine[38];
                String notAfter = simplified ? serieLine[4] : serieLine[17];
                if (!notBefore.isEmpty() && !notAfter.isEmpty()) {
                    Resource event = workModel.createResource(BDR+"EW"+serieID+"_01");
                    workModel.add(work, workModel.createProperty(BDO, "workEvent"), event);
                    workModel.add(event, RDF.type, workModel.createResource(BDO+"CopyEvent"));
                    if (simplified && !serieLine[13].isEmpty()) {
                        workModel.add(event, workModel.createProperty(BDO, "eventWhere"), workModel.createResource(BDR+serieLine[13]));
                    }
                    // TODO: add locations in other eap sheets
                    if (notBefore.equals(notAfter)) {
                        workModel.add(event, workModel.createProperty(BDO, "onYear"), workModel.createTypedLiteral(Integer.valueOf(notBefore), XSDDatatype.XSDinteger));    
                    } else {
                        workModel.add(event, workModel.createProperty(BDO, "notBefore"), workModel.createTypedLiteral(Integer.valueOf(notBefore), XSDDatatype.XSDinteger));
                        workModel.add(event, workModel.createProperty(BDO, "notAfter"), workModel.createTypedLiteral(Integer.valueOf(notAfter), XSDDatatype.XSDinteger));
                    }
                }
                
                
                // Item model
                Model itemModel = ModelFactory.createDefaultModel();
                setPrefixes(itemModel);
                Resource item = createRoot(itemModel, BDR+"I"+serieID, BDO+"ItemImageAsset");
                Resource admItem = createAdminRoot(item);
                res.add(item);

                workModel.add(work, workModel.createProperty(BDO,"workHasItem"), item);

                // Item adm:AdminData
                ldEAP = itemModel.createResource(BDA+"LD_EAP");
                itemModel.add(admItem, RDF.type, itemModel.createResource(ADM+"AdminData"));
                itemModel.add(admItem, itemModel.getProperty(ADM+"status"), itemModel.createResource(BDR+"StatusReleased"));
                itemModel.add(admItem, itemModel.createProperty(ADM, "contentLegal"), ldEAP); // ?
                itemModel.add(admItem, itemModel.createProperty(ADM, "metadataLegal"), ldEAP); // ?
                
                itemModel.add(item, itemModel.createProperty(BDO, "itemForWork"), itemModel.createResource(BDR+"W"+serieID));
                
                List<String[]> volumes = getVolumes(serie);
                int numVol=0;
                for(int x=0;x<volumes.size();x++) {
                    final String[] volume = volumes.get(x);
                    String ref=(simplified ? volume[1] : volume[4]).replace('/', '-');
                    //System.out.println(Arrays.toString(volume));
                    Resource vol = itemModel.createResource(BDR+"V"+ref);
                    itemModel.add(item, itemModel.createProperty(BDO, "itemHasVolume"), vol);
                    itemModel.add(vol, RDF.type, itemModel.createResource(BDO+"VolumeImageAsset"));
                    String name = simplified ? volume[9] : volume[39];
                    //tmp=tmp.substring(tmp.indexOf("containing")).split(" ")[1];
                    //itemModel.add(vol, itemModel.createProperty(BDO,"imageCount"),itemModel.createTypedLiteral(Integer.parseInt(tmp), XSDDatatype.XSDinteger));
                    itemModel.add(vol, itemModel.createProperty(BDO,"hasIIIFManifest"),itemModel.createResource(ManifestPREFIX+ref+"/manifest"));
                    //itemModel.add(vol, itemModel.createProperty(BDO,"volumeName"),getLiteral(name, workModel));
                    itemModel.add(vol, SKOS.prefLabel,getLiteral(name, workModel));
                    itemModel.add(vol, itemModel.createProperty(BDO,"volumeNumber"),itemModel.createTypedLiteral(getVolNum(volume), XSDDatatype.XSDinteger));
                    itemModel.add(vol, itemModel.createProperty(BDO,"volumeOf"),item);
                    res.add(vol);
                    
                    // Volume adm:AdminData
                    Resource admVol = getAdminData(vol);
                    itemModel.add(admVol, RDF.type, itemModel.createResource(ADM+"AdminData"));
                    origUrl = ManifestPREFIX+ref;
                    itemModel.add(admVol, itemModel.createProperty(ADM, "originalRecord"), itemModel.createTypedLiteral(origUrl, XSDDatatype.XSDanyURI));                

                    numVol++;
                }
                itemModel.add(item, itemModel.createProperty(BDO, "itemVolumes"), itemModel.createTypedLiteral(numVol));
                workModel.add(work, workModel.createProperty(BDO, "workNumberOfVolumes"), workModel.createTypedLiteral(numVol, XSDDatatype.XSDinteger));
            }
        }
        return res;
    }

    public static void EAPFondsDoTransfer() throws IOException {
        EAPFondsTransfer tr = new EAPFondsTransfer("EAP310.csv", false);
        tr.writeEAPFiles(tr.getResources());
        tr = new EAPFondsTransfer("EAP039.csv", false);
        tr.writeEAPFiles(tr.getResources());
        tr = new EAPFondsTransfer("eap2.csv", true);
        tr.writeEAPFiles(tr.getResources());
    }

}
