package de.mpg.escidoc.faces.container.beans;

import java.util.ArrayList;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.print.attribute.standard.Severity;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import de.mpg.escidoc.faces.beans.SessionBean;
import de.mpg.escidoc.faces.container.FacesContainerVO;
import de.mpg.escidoc.faces.container.collection.CollectionController;
import de.mpg.escidoc.faces.container.collection.CollectionSession;
import de.mpg.escidoc.faces.container.collection.CollectionVO;
import de.mpg.escidoc.faces.metadata.Metadata;
import de.mpg.escidoc.faces.metadata.MetadataBean;
import de.mpg.escidoc.faces.metadata.ScreenConfiguration;
import de.mpg.escidoc.faces.metadata.MetadataBean.ConstraintBean;
import de.mpg.escidoc.faces.metadata.helper.VocabularyHelper;
import de.mpg.escidoc.faces.util.BeanHelper;
import de.mpg.escidoc.faces.util.ContextHelper;
import de.mpg.escidoc.faces.util.UserHelper;
import de.mpg.escidoc.services.common.referenceobjects.ContextRO;
import de.mpg.escidoc.services.common.valueobjects.GrantVO;
import de.mpg.escidoc.services.common.valueobjects.GrantVO.PredefinedRoles;
import de.mpg.escidoc.services.common.valueobjects.metadata.CreatorVO;
import de.mpg.escidoc.services.common.valueobjects.metadata.OrganizationVO;
import de.mpg.escidoc.services.common.valueobjects.metadata.PersonVO;
import de.mpg.escidoc.services.common.valueobjects.metadata.CreatorVO.CreatorRole;

/**
 * JSF bean for {@link CollectionVO}
 * 
 * @author saquet
 *
 */
public class CollectionBean 
{
	/**
	 * All type of web pages supported by {@link CollectionBean}
	 * @author saquet
	 *
	 */
	public enum CollectionPageType
	{
		CREATE, EDIT, VIEW;
	}
	
	private CollectionVO collection = null;
	private CollectionController collectionController = null;
	private SessionBean sessionBean = null;
	private CollectionSession collectionSession = null;
	private ScreenConfiguration screenManager = null;
	private List<SelectItem> collectionsMenu = null;
	private CollectionPageType pageType = CollectionPageType.CREATE;
	private HttpServletRequest request = null;
	private FacesContext fc = null;
	private List<SelectItem> userDepositorGrants = null;
	private String selectedContext = null;
	
	
	private MdProfileSession mdProfileSession = null;
	private List<Metadata> metadataList = new ArrayList<Metadata>();
	private List<SelectItem> metadataMenu = new ArrayList<SelectItem>();
	private int profilePosition;
	private int authorPosition;
	private int organizationPosition;
	
	private static Logger logger = Logger.getLogger(CollectionBean.class);
	
	/**
	 * Default Constructor
	 */
	public CollectionBean() 
	{
		collectionController = new CollectionController();
		sessionBean = (SessionBean) BeanHelper.getSessionBean(SessionBean.class);
		collectionSession = (CollectionSession) BeanHelper.getSessionBean(CollectionSession.class);
		mdProfileSession = (MdProfileSession) BeanHelper.getSessionBean(MdProfileSession.class);
		collectionsMenu = new ArrayList<SelectItem>();
		userDepositorGrants = new ArrayList<SelectItem>();
		fc =  FacesContext.getCurrentInstance();
		request = (HttpServletRequest) fc.getExternalContext().getRequest();
		metadataList = mdProfileSession.getMetadataList();
		try 
		{
			init();
		} 
		catch (Exception e) 
		{
			sessionBean.setMessage("Error Initializing Collection Bean" + e);
			logger.error("Error initializing Collection Bean", e);
		}
	}
	
	/**
	 * Manage initialization of the bean according to url's parameters
	 * @throws Exception
	 */
	public void init() throws Exception
	{
		if ("init".equals(request.getParameter("action")))
		{
		    mdProfileSession.getMetadataBeanList().clear();
		}
	    	
		for (Metadata m : metadataList)
		{
		    if (m.getSimpleValue() == null)
		    {
			m.setSimpleValue("");
		    }
		    metadataMenu.add(new SelectItem(m.getIndex(), m.getLabel()));
		}
		
		String collectionId = request.getParameter("id"); 
		String page = request.getParameter("page");
		
		if (collectionId != null && CollectionPageType.EDIT.name().equalsIgnoreCase(page)) 
		{
			pageType = CollectionPageType.EDIT;
			collection = (CollectionVO)collectionController.retrieve(collectionId, sessionBean.getUserHandle());
		}
		else if (collectionId != null && CollectionPageType.VIEW.name().equalsIgnoreCase(page))
		{
			pageType = CollectionPageType.VIEW;
			collection = (CollectionVO)collectionController.retrieve(collectionId, sessionBean.getUserHandle());
		}
		else 
		{
			pageType = CollectionPageType.CREATE;
			collection = collectionSession.getCurrent();
		}
		
		collectionSession.setCurrent(collection);
		
		for (FacesContainerVO c : collectionSession.getCollectionList().getList()) 
		{
			collectionsMenu.add(
					new SelectItem(c.getContentModel()
					, c.getMdRecord().getTitle().getValue()));
		}
		
		for (GrantVO g : UserHelper.getGrants(sessionBean.getUserHandle()))
		{
		    if (PredefinedRoles.DEPOSITOR.frameworkValue().equals(g.getRole()))
		    {
			userDepositorGrants.add(new SelectItem(g.getObjectRef()
				, ContextHelper.getContext(g.getObjectRef(), sessionBean.getUserHandle()).getName()));
		    }
		}		
		selectedContext = "";
	}
		
	/**
	 * Method to:
	 * <br> * Create a new collection
	 * <br> * Update a collection with new values
	 * @throws Exception
	 */
	public void save() throws Exception
	{
	    if (valid())
	    {
	    	collection.getMdProfile().getMetadataList().clear();
		collection.getMdProfile().getMetadataList().add(new Metadata("title","Title","http://purl.org/dc/elements/1.1"));
		collection.getMdProfile().getMetadataList().add(new Metadata("description","Description","http://purl.org/dc/elements/1.1"));
		collection.getMdProfile().setName(collection.getMdRecord().getTitle().getValue());
		collection.getMdProfile().setId(collection.getMdRecord().getTitle().getValue());
		
		int i = 2;
		for (MetadataBean m : mdProfileSession.getMetadataBeanList())
		{
		    collection.getMdProfile().getMetadataList().add(new Metadata(m.getCurrent()));
		    collection.getMdProfile().getMetadataList().get(i).getConstraint().clear();
		    
		    for (ConstraintBean c : m.getConstraints())
		    {
			if (!"".equals(c.getValue()))
			{
			    collection.getMdProfile().getMetadataList().get(i).getConstraint().add(c.getValue());
			} 
		    }
		    i++;
		}
		
		if (CollectionPageType.CREATE.equals(this.pageType)) 
		{
			collectionController.create(collection, sessionBean.getUserHandle());
			sessionBean.setInformation("Collection successfully created");
		}
		if (CollectionPageType.EDIT.equals(this.pageType)) 
		{
			collectionController.edit(collection, sessionBean.getUserHandle());
			sessionBean.setInformation("Collection successfully edited");
		}
	    }
	}
	
	/**
	 * True if formular is valid. 
	 * @return
	 */
	public boolean valid()
	{
	    boolean valid = true;
	    boolean hasAuthor = false;
	    
	    if (collection.getMdRecord().getTitle() == null
		    || collection.getMdRecord().getTitle().getValue() == null
		    || "".equals(collection.getMdRecord().getTitle().getValue()))
	    {
		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR ,sessionBean.getMessage("collection_create_error_title"),sessionBean.getMessage("collection_create_error_title")));
		valid =  false;
	    }
	    
	    for (CreatorVO c : collection.getMdRecord().getCreators())
	    {
		boolean hasOrganization = true;
		
		if (!"".equals(c.getPerson().getFamilyName()))
		{
		    hasAuthor = true;
		}
		
		for (OrganizationVO o : c.getPerson().getOrganizations())
		{
		    if ("".equals(o.getName().getValue()) && !"".equals(c.getPerson().getFamilyName()))
		    {
			hasOrganization = false;
		    }
		}
		
		if (!hasOrganization)
		{
		    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Author needs at least one organization"));
		    valid = false;
		}
	    }
	    
	    if (!hasAuthor)
	    {
		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Author needs at least one Author"));
		valid = false;
	    }	  
	    return valid;
	}
	
	public void addAuthor()
	{
	    collectionSession.getCurrent().getMdRecord().getCreators().add(authorPosition + 1, collection.getMdRecord().initNewCreator());
	}
	
	public void removeAuthor()
	{
	    if (authorPosition > 0)
	    {
		  collectionSession.getCurrent().getMdRecord().getCreators().remove(authorPosition);
	    }
	}
	
	public void addCollection()
	{
	    OrganizationVO newOrg = collection.getMdRecord().initNewCreator().getPerson().getOrganizations().get(0);
	    collectionSession.getCurrent().getMdRecord().getCreators().get(authorPosition).getPerson().getOrganizations().add(organizationPosition , newOrg);
	}
	
	public void removeCollection()
	{
	    if (organizationPosition > 0)
	    {
		collectionSession.getCurrent().getMdRecord().getCreators().get(authorPosition).getPerson().getOrganizations().remove(organizationPosition);
	    }
	}
	
	public int getAuthorPosition()
	{
	    return authorPosition;
	}
	
	public void setAuthorPosition(int pos)
	{
	    this.authorPosition = pos;
	}
	
	/**
	 * @return the collectionPosition
	 */
	public int getOrganizationPosition()
	{
	    return organizationPosition;
	}

	/**
	 * @param collectionPosition the collectionPosition to set
	 */
	public void setOrganizationPosition(int organizationPosition)
	{
	    this.organizationPosition = organizationPosition;
	}

	public CollectionVO getCollection() 
	{
		return collection;
	}

	public void setCollection(CollectionVO collectionVO) 
	{
		this.collection = collectionVO;
	}

	public ScreenConfiguration getScreenManager() 
	{
		return screenManager;
	}

	public void setScreenManager(ScreenConfiguration screenManager) 
	{
		this.screenManager = screenManager;
	}

	public List<SelectItem> getCollectionsMenu() 
	{
		return collectionsMenu;
	}

	public void setCollectionsMenu(List<SelectItem> collectionsMenu) 
	{
		this.collectionsMenu = collectionsMenu;
	}

	/**
	 * @return the pageType
	 */
	public CollectionPageType getPageType() 
	{
		return pageType;
	}

	/**
	 * @param pageType the pageType to set
	 */
	public void setPageType(CollectionPageType pageType) 
	{
		this.pageType = pageType;
	}
	
	 /**
     * JSF Listener for the title value
     * @param event
     */
    public void titleListener(ValueChangeEvent event) 
    {
    	if (event.getNewValue() != null && !event.getNewValue().equals(event.getOldValue())) 
    	{
    	    collectionSession.getCurrent().getMdRecord().getTitle().setValue(event.getNewValue().toString());
	}
    }
    
    /**
     * JSF Listener for the abstract value
     * @param event
     */
    public void descriptionListener(ValueChangeEvent event)
    {
    	if (event.getNewValue() != null && !event.getNewValue().equals(event.getOldValue())) 
    	{
    	    collectionSession.getCurrent().getMdRecord().getAbstracts().get(0).setValue(event.getNewValue().toString());
	}
    }

    /**
     * @return the userDepositorGrants
     */
    public List<SelectItem> getUserDepositorGrants()
    {
        return userDepositorGrants;
    }

    /**
     * @param userDepositorGrants the userDepositorGrants to set
     */
    public void setUserDepositorGrants(List<SelectItem> userDepositorGrants)
    {
        this.userDepositorGrants = userDepositorGrants;
    }

    /**
     * @return the selectedContext
     */
    public String getSelectedContext()
    {
        return selectedContext;
    }

    /**
     * @param selectedContext the selectedContext to set
     */
    public void setSelectedContext(String selectedContext)
    {
        this.selectedContext = selectedContext;
    }
    
    
    public void selectContextListener(ValueChangeEvent event)
    {
	if (event.getNewValue() != null && !event.getNewValue().equals(event.getOldValue())) 
    	{
	    selectedContext = event.getNewValue().toString();
	    collection.setContext(new ContextRO(selectedContext));
	}
    }
    
    public String addMetadata()
    {
    	if(mdProfileSession.getMetadataBeanList().size()==0)
    	{
    	mdProfileSession.getMetadataBeanList().add(new MetadataBean(metadataList));
    	}
    	else
    	{
    	mdProfileSession.getMetadataBeanList().add(getProfilePosition() + 1, new MetadataBean(metadataList));
    	}
    	return "";
    }
    
    public String removeMetadata()
    {
	mdProfileSession.getMetadataBeanList().remove(getProfilePosition());
	return "";	
    }
    
    /**
     * @return the mdProfile
     */
    public List<MetadataBean> getMetadataBeanList()
    {
        return mdProfileSession.getMetadataBeanList();
    }

    /**
     * @param mdProfile the mdProfile to set
     */
    public void setMetadataBeanList(List<MetadataBean> metadataBeanList)
    {
        this.mdProfileSession.setMetadataBeanList(metadataBeanList);
    }
    
    /**
     * @return the metadataMenu
     */
    public List<SelectItem> getMetadataMenu()
    {
        return metadataMenu;
    }

    /**
     * @param metadataMenu the metadataMenu to set
     */
    public void setMetadataMenu(List<SelectItem> metadataMenu)
    {
        this.metadataMenu = metadataMenu;
    }

	public void setProfilePosition(int profilePosition) {
		this.profilePosition = profilePosition;
	}

	public int getProfilePosition() {
		return profilePosition;
	}
    
}
