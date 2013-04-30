package ingest;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import de.mpg.imeji.logic.ingest.jaxb.JaxbUtil;
import de.mpg.imeji.logic.ingest.mapper.ItemMapper;
import de.mpg.imeji.logic.ingest.parser.ItemParser;
import de.mpg.imeji.logic.ingest.parser.ProfileParser;
import de.mpg.imeji.logic.ingest.vo.Items;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.Metadata;
import de.mpg.imeji.logic.vo.MetadataProfile;
import de.mpg.imeji.logic.vo.MetadataSet;
import de.mpg.imeji.logic.vo.predefinedMetadata.Text;

public class IngestTest
{
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
    }

    @Test
    public void itemsParsingTest() throws JAXBException, SAXException
    {
        String xmlFilename = "./src/test/resources/ingest/test/items.xml";
        ItemParser ip = new ItemParser();
        List<Item> itemList = ip.parseItemList(new File(xmlFilename));
        ItemMapper im = new ItemMapper(itemList);
        Collection<Item> mappedItemList = im.getMappedItemObjects();
        Items items = new Items();
        items.setItem(new ArrayList<Item>(mappedItemList));
        try
        {
            JaxbUtil.toString(items);            
        }
        catch (JAXBException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @Test
    public void itemsParsingWrite2FileTest() throws JAXBException, SAXException, IOException
    {
        String xmlFilename = "./src/test/resources/ingest/test/items.xml";
        String xmlOutputFilename = "./src/test/resources/ingest/test/items-out.xml";
        
        
        
        ItemParser ip = new ItemParser();
        List<Item> itemList = ip.parseItemList(new File(xmlFilename));
        ItemMapper im = new ItemMapper(itemList);
        Collection<Item> mappedItemList = im.getMappedItemObjects();
        Items items = new Items();
        items.setItem(new ArrayList<Item>(mappedItemList));
        try
        {
            JaxbUtil.writeToOutputStream(items,new FileOutputStream(xmlOutputFilename));            
        }
        catch (JAXBException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void profileParsingTest() throws JAXBException, SAXException
    {    	
        String xmlFilename = "./src/test/resources/ingest/test/mdp.xml";
        ProfileParser pp = new ProfileParser();
        MetadataProfile profile = pp.parse(new File(xmlFilename));
        try
        {
            JaxbUtil.toString(profile);
        }
        catch (JAXBException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @Test
    public void testItemView() throws URISyntaxException
    {
        try
        {

            Item item = new Item();
            List<MetadataSet> mdsList = item.getMetadataSets();
            MetadataSet mds = new MetadataSet();
            Collection<Metadata> mdC = new LinkedList<Metadata>();
            
            item.setCreatedBy(new URI("http://zuse2.zib.de/user/admin%40imeji.org"));
            item.setCreated(Calendar.getInstance());
            item.setModified(Calendar.getInstance());             
            
            
            Text text = new Text();
            text.setId(new URI("http://imeji.org/terms/metadata/eWqXRoUcpi9XvXg"));
            text.setStatement(new URI("http://imeji.org/terms/statement/h3uvXnK61DoMvPJB"));
            text.setText("a new text here");
            mdC.add(text);
            
            
            Text text2 = new Text();
            text2.setId(new URI("http://imeji.org/terms/metadata/5WqXRoUcpi9XvX1"));
            text2.setStatement(new URI("http://imeji.org/terms/statement/hbuvXnK61DoMvPJA"));
            text2.setText("a new text here");
            mdC.add(text2);
            
            mds.setMetadata(mdC);
            mdsList.add(mds);
            item.setMetadataSets(mdsList);
            JaxbUtil.toString(item);
        }
        catch (JAXBException e)
        {
            e.printStackTrace();
            fail("JAXBException");
        }
    }
}
