/**
 * License: src/main/resources/license/escidoc.license
 */
package de.mpg.imeji.logic.controller;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import de.escidoc.core.client.Authentication;
import de.escidoc.core.client.ItemHandlerClient;
import de.mpg.imeji.logic.ImejiBean2RDF;
import de.mpg.imeji.logic.ImejiJena;
import de.mpg.imeji.logic.ImejiRDF2Bean;
import de.mpg.imeji.logic.search.ImejiSPARQL;
import de.mpg.imeji.logic.search.Search;
import de.mpg.imeji.logic.search.SearchResult;
import de.mpg.imeji.logic.search.vo.SearchQuery;
import de.mpg.imeji.logic.search.vo.SortCriterion;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.Grant;
import de.mpg.imeji.logic.vo.Grant.GrantType;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.Item.Visibility;
import de.mpg.imeji.logic.vo.Metadata;
import de.mpg.imeji.logic.vo.MetadataSet;
import de.mpg.imeji.logic.vo.Properties.Status;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.presentation.util.LoginHelper;
import de.mpg.imeji.presentation.util.PropertyReader;
import de.mpg.j2j.helper.J2JHelper;

public class ItemController extends ImejiController
{
    private String additionalQuery = "";
    private static Logger logger = null;
    private static ImejiRDF2Bean imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
    private static ImejiBean2RDF imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);

    public ItemController(User user)
    {
        super(user);
        logger = Logger.getLogger(ItemController.class);
    }

    public void create(Item img, URI coll) throws Exception
    {
        CollectionController cc = new CollectionController(user);
        CollectionImeji ic = cc.retrieve(coll);
        writeCreateProperties(img, user);
        if (Status.PENDING.equals(ic.getStatus()))
            img.setVisibility(Visibility.PRIVATE);
        else
            img.setVisibility(Visibility.PUBLIC);
        img.setCollection(coll);
        img.setId(ObjectHelper.getURI(Item.class, Integer.toString(getUniqueId())));
        img.getMetadataSet().setProfile(ic.getProfile());
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        imejiBean2RDF.create(imejiBean2RDF.toList(img), user);
        ic.getImages().add(img.getId());
        cc.update(ic);
    }

    public void createTest(Item img, URI coll) throws Exception
    {
        writeCreateProperties(img, user);
        img.setVisibility(Visibility.PUBLIC);
        img.setCollection(coll);
        img.setId(ObjectHelper.getURI(Item.class, Integer.toString(getUniqueId())));
        img.getMetadataSet().setId(ObjectHelper.getURI(Item.class, Integer.toString(getUniqueId())));
        img.getMetadataSet().setProfile(URI.create("http://imeji.org/mdProfile/3"));
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        imejiBean2RDF.create(imejiBean2RDF.toList(img), user);
    }

    public void create(Collection<Item> items, URI coll) throws Exception
    {
        CollectionController cc = new CollectionController(user);
        CollectionImeji ic = cc.retrieve(coll);
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        for (Item img : items)
        {
            writeCreateProperties(img, user);
            if (Status.PENDING.equals(ic.getStatus()))
                img.setVisibility(Visibility.PRIVATE);
            else
                img.setVisibility(Visibility.PUBLIC);
            img.setCollection(coll);
            img.setId(ObjectHelper.getURI(Item.class, Integer.toString(getUniqueId())));
            img.getMetadataSet().setProfile(ic.getProfile());
            // imejiBean2RDF.create(imejiBean2RDF.toList(img), user);
            ic.getImages().add(img.getId());
        }
        imejiBean2RDF.create(J2JHelper.cast2ObjectList((List<?>)items), user);
        cc.update(ic);
    }

    public void update(Item img) throws Exception
    {
        Collection<Item> im = new ArrayList<Item>();
        im.add(img);
        update(im);
    }

    public void update(Collection<Item> items) throws Exception
    {
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        List<Object> imBeans = new ArrayList<Object>();
        logger.info("before init");
        long beforeinit = System.currentTimeMillis();
        for (Item item : items)
        {
            writeUpdateProperties(item, user);
            imBeans.add(initAllMetadata(item));
        }
        long afterinit = System.currentTimeMillis();
        logger.info("upadate item initialized = " + Long.valueOf(afterinit - beforeinit));
        long before = System.currentTimeMillis();
        imejiBean2RDF.update(imBeans, user);
        long after = System.currentTimeMillis();
        logger.info("item controller update = " + Long.valueOf(after - before));
    }

    private Item initAllMetadata(Item item)
    {
        for (MetadataSet mds : item.getMetadataSets())
        {
            for (Metadata md : mds.getMetadata())
            {
                md.init();
            }
        }
        return item;
    }

    /**
     * User ObjectLoader to load image
     * 
     * @param imgUri
     * @return
     * @throws Exception
     */
    public Item retrieve(URI imgUri) throws Exception
    {
        imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
        return (Item)imejiRDF2Bean.load(imgUri.toString(), user, new Item());
    }

    /**
     * User ObjectLoader instead
     * 
     * @deprecated
     * @param id
     * @return
     * @throws Exception
     */
    public Item retrieve(String id) throws Exception
    {
        imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
        return (Item)imejiRDF2Bean.load(ObjectHelper.getURI(Item.class, id).toString(), user, new Item());
    }

    @Deprecated
    public Collection<Item> retrieveAll()
    {
        imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
        // return imejiRDF2Bean.load(Image.class);
        return new ArrayList<Item>();
    }

    /**
     * NOT WORKING
     * 
     * @param uri
     */
    public void getGraph(URI uri)
    {
        additionalQuery = " . <"
                + uri.toString()
                + "> <http://imeji.org/terms/item/metadata> ?md . ?md <http://www.w3.org/2000/01/rdf-schema#member> ?list . ?list <http://purl.org/dc/terms/type> ?type";
        // QuerySPARQL querySPARQL = new QuerySPARQLImpl();
        // String query = querySPARQL.createConstructQuery(new ArrayList<SearchCriterion>(), null,
        // "http://imeji.org/terms/item", additionalQuery , "?s=<http://imeji.org/terms/item/111>", 1, 0, user, false);
        // ImejiSPARQL.execConstruct(query).write(System.out, "RDF/XML-ABBREV");
    }

    /**
     * Get the number of all images
     * 
     * @return
     */
    public int allImagesSize()
    {
        return ImejiSPARQL.execCount("SELECT count(DISTINCT ?s) WHERE { ?s a <http://imeji.org/terms/item>}",
                ImejiJena.imageModel);
    }

    public SearchResult searchImages(SearchQuery searchQuery, SortCriterion sortCri)
    {
        Search search = new Search("http://imeji.org/terms/item", null);
        return search.search(searchQuery, sortCri, simplifyUser(null));
    }

    public SearchResult searchImagesInContainer(URI containerUri, SearchQuery searchQuery, SortCriterion sortCri,
            int limit, int offset)
    {
        Search search = new Search("http://imeji.org/terms/item", containerUri.toString());
        return search.search(searchQuery, sortCri, simplifyUser(containerUri));
    }

    public int countImages(SearchQuery searchQuery)
    {
        Search search = new Search("http://imeji.org/terms/item", null);
        return search.search(searchQuery, null, simplifyUser(null)).getNumberOfRecords();
    }

    public int countImages(SearchQuery searchQuery, List<String> allImages)
    {
        Search search = new Search("http://imeji.org/terms/item", null);
        return search.search(allImages, searchQuery, null, simplifyUser(null)).getNumberOfRecords();
    }

    public int countImagesInContainer(URI containerUri, SearchQuery searchQuery)
    {
        Search search = new Search("http://imeji.org/terms/item", containerUri.toString());
        int size = 0;
        if (searchQuery.isEmpty())
        {
            size = search
                    .searchSimpleForQuery(
                            "PREFIX fn: <http://www.w3.org/2005/xpath-functions#> SELECT ?s WHERE { ?s <http://imeji.org/terms/collection> <"
                                    + containerUri
                                    + "> . ?s <http://imeji.org/terms/status> ?status   .FILTER(?status!=<http://imeji.org/terms/status#WITHDRAWN>) }",
                            new SortCriterion()).size();
        }
        else
        {
            size = search.search(searchQuery, new SortCriterion(), simplifyUser(containerUri)).getNumberOfRecords();
        }
        return size;
    }

    public int countImagesInContainer(URI containerUri, SearchQuery searchQuery, List<String> containerImages)
    {
        Search search = new Search("http://imeji.org/terms/item", containerUri.toString());
        return search.search(containerImages, searchQuery, new SortCriterion(), simplifyUser(containerUri)).getNumberOfRecords();
    }

    public Collection<Item> loadItems(List<String> uris, int limit, int offset)
    {
        long before = System.currentTimeMillis();
        ImejiRDF2Bean reader = new ImejiRDF2Bean(ImejiJena.imageModel);
        int counter = 0;
        List<Object> l = new ArrayList<Object>();
        for (String s : uris)
        {
            if (offset <= counter && (counter < (limit + offset) || limit == -1))
            {
                l.add(J2JHelper.setId(new Item(), URI.create(s)));
            }
            counter++;
        }
        try
        {
            l = reader.load(l, user);
            List<Item> items = new ArrayList<Item>();
            for (Object o : l)
            {
                items.add((Item)o);
            }
            long after = System.currentTimeMillis();
            logger.info(items.size() + " items loaded in " + Long.valueOf(after - before));
            return items;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error loading images:", e);
        }
    }

    /**
     * Increase performance by restricting grants to the only grants needed
     * 
     * @param user
     * @return
     */
    public User simplifyUser(URI containerUri)
    {
        if (user == null)
        {
            return null;
        }
        User simplifiedUser = new User();
        for (Grant g : user.getGrants())
        {
            if (GrantType.SYSADMIN.equals(g.asGrantType()))
            {
                simplifiedUser.getGrants().add(g);
            }
            else if (containerUri != null && containerUri.toString().contains("collection")
                    && containerUri.toString().equals(g.getGrantFor().toString()))
            {
                simplifiedUser.getGrants().add(g);
            }
            else if (containerUri != null && containerUri.toString().contains("album")
                    && g.getGrantFor().toString().contains("collection"))
            {
                simplifiedUser.getGrants().add(g);
            }
            else if (containerUri == null && g.getGrantFor() != null
                    && g.getGrantFor().toString().contains("collection"))
            {
                simplifiedUser.getGrants().add(g);
            }
            else
            {
                // simplifiedUser.getGrants().add(g);
            }
        }
        return simplifiedUser;
    }

    public void delete(Item img, User user) throws Exception
    {
        if (img != null)
        {
            imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
            imejiBean2RDF.delete(imejiBean2RDF.toList(img), user);
            removeImageFromEscidoc(img.getEscidocId());
        }
    }

    public void release(Item img) throws Exception
    {
        if (Status.PENDING.equals(img.getStatus()))
        {
            img.setStatus(Status.RELEASED);
            img.setVisibility(Visibility.PUBLIC);
            update(img);
        }
    }

    public void withdraw(Item img) throws Exception
    {
        if (img.getStatus().equals(Status.RELEASED))
        {
            img.setStatus(Status.WITHDRAWN);
            img.setVisibility(Visibility.PUBLIC);
            update(img);
            if (img.getEscidocId() != null)
            {
                removeImageFromEscidoc(img.getEscidocId());
                img.setEscidocId(null);
            }
        }
        else
            throw new RuntimeException("Only released images can be discarded: " + img.getId() + " has status "
                    + img.getStatus());
    }

    public void removeImageFromEscidoc(String id)
    {
        try
        {
            String username = PropertyReader.getProperty("imeji.escidoc.user");
            String password = PropertyReader.getProperty("imeji.escidoc.password");
            Authentication auth = new Authentication(new URL(
                    PropertyReader.getProperty("escidoc.framework_access.framework.url")), username, password);
            ItemHandlerClient handler = new ItemHandlerClient(auth.getServiceAddress());
            handler.setHandle(auth.getHandle());
            handler.delete(id);
        }
        catch (Exception e)
        {
            logger.error("Error removing image from eSciDoc (" + id + ")", e);
            throw new RuntimeException("Error removing image from eSciDoc (" + id + ")", e);
        }
    }

    public String getEscidocUserHandle() throws Exception
    {
        String userName = PropertyReader.getProperty("imeji.escidoc.user");
        String password = PropertyReader.getProperty("imeji.escidoc.password");
        return LoginHelper.login(userName, password);
    }

    @Override
    @Deprecated
    protected String getSpecificFilter() throws Exception
    {
        // Add filters for user management
        String filter = "(";
        if (user == null)
        {
            filter += "?collStatus = <<http://imeji.org/terms/status#RELEASED> && ?visibility = <http://imeji.org/terms/item/visibility/PUBLIC>";
        }
        else
        {
            String userUri = "http://xmlns.com/foaf/0.1/Person/" + URLEncoder.encode(user.getEmail(), "UTF-8");
            filter += "(?collStatus = <<http://imeji.org/terms/status#RELEASED> && ?visibility = <http://imeji.org/terms/item/visibility/PUBLIC>)";
            filter += " || ?collCreatedBy=<" + userUri + ">";
            for (Grant grant : user.getGrants())
            {
                switch (grant.asGrantType())
                {
                    case CONTAINER_ADMIN: // Add specifics here
                    default:
                        if (grant.getGrantFor() != null)
                            filter += " || ?collection=<" + grant.getGrantFor().toString() + ">";
                }
            }
        }
        filter += ")";
        return filter;
    }

    @Override
    protected String getSpecificQuery() throws Exception
    {
        return additionalQuery
                + " . ?s <http://imeji.org/terms/collection> ?collection . ?s <http://imeji.org/terms/visibility> ?visibility . ?collection <http://imeji.org/terms/properties> ?collprops . ?collprops <http://imeji.org/terms/createdBy> ?collCreatedBy . ?collprops <http://imeji.org/terms/status> ?collStatus ";
    }
}
