/**
 * License: src/main/resources/license/escidoc.license
 */
package de.mpg.imeji.logic.controller;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.mpg.imeji.logic.storage.util.StorageUtils.calculateChecksum;
import static de.mpg.imeji.logic.storage.util.StorageUtils.getMimeType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.mpg.imeji.exceptions.AuthenticationError;
import de.mpg.imeji.exceptions.BadRequestException;
import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.exceptions.NotAllowedError;
import de.mpg.imeji.exceptions.UnprocessableError;
import de.mpg.imeji.logic.Imeji;
import de.mpg.imeji.logic.ImejiSPARQL;
import de.mpg.imeji.logic.auth.util.AuthUtil;
import de.mpg.imeji.logic.reader.ReaderFacade;
import de.mpg.imeji.logic.search.Search;
import de.mpg.imeji.logic.search.Search.SearchType;
import de.mpg.imeji.logic.search.SearchFactory;
import de.mpg.imeji.logic.search.SearchResult;
import de.mpg.imeji.logic.search.query.SPARQLQueries;
import de.mpg.imeji.logic.search.vo.SearchQuery;
import de.mpg.imeji.logic.search.vo.SortCriterion;
import de.mpg.imeji.logic.storage.Storage;
import de.mpg.imeji.logic.storage.Storage.FileResolution;
import de.mpg.imeji.logic.storage.StorageController;
import de.mpg.imeji.logic.storage.UploadResult;
import de.mpg.imeji.logic.storage.internal.InternalStorageManager;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.Container;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.Item.Visibility;
import de.mpg.imeji.logic.vo.Metadata;
import de.mpg.imeji.logic.vo.MetadataSet;
import de.mpg.imeji.logic.vo.Properties.Status;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.logic.writer.WriterFacade;
import de.mpg.imeji.presentation.util.ImejiFactory;
import de.mpg.imeji.presentation.util.PropertyReader;
import de.mpg.imeji.rest.process.CommonUtils;
import de.mpg.j2j.helper.J2JHelper;

/**
 * Implements CRUD and Search methods for {@link Item}
 * 
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
public class ItemController extends ImejiController {
	private static Logger logger = Logger.getLogger(ItemController.class);
	private static final ReaderFacade reader = new ReaderFacade(
			Imeji.imageModel);
	private static final WriterFacade writer = new WriterFacade(
			Imeji.imageModel);
	public static final String NO_THUMBNAIL_FILE_NAME = "noThumbnail.png";
	private static String NO_THUMBNAIL_URL;

	/**
	 * Controller constructor
	 */
	public ItemController() {
		super();
		try {
			NO_THUMBNAIL_URL = PropertyReader.getProperty("imeji.instance.url")
					+ "/resources/icon/" + NO_THUMBNAIL_FILE_NAME;
		} catch (Exception e) {
			throw new RuntimeException("Error reading property: ",
					e);
		}
	}

	/**
	 * Create an {@link Item} in a {@link CollectionImeji}
	 *
	 * @param item
	 * @param coll
	 * @param user
	 * @throws ImejiException
	 * @return
	 */
	public Item create(Item item, URI coll, User user) throws ImejiException {
		Collection<Item> l = new ArrayList<Item>();
		l.add(item);
		create(l, coll, user);
		return item;
	}

	/**
	 * Create an {@link Item} for a {@link File}.
	 * 
	 * @param f
	 * @param filename
	 *            (optional)
	 * @param c
	 *            - the collection in which the file is uploaded
	 * @param user
	 * @return
	 * @throws ImejiException
	 */
	public Item createWithFile(Item item, File f, String filename,
			CollectionImeji c, User user) throws ImejiException {
		if (!AuthUtil.staticAuth().create(user, item))
			throw new NotAllowedError(
					"User not Allowed to upload files in collection "
							+ c.getIdString());
		StorageController sc = new StorageController();
		String mimeType = getMimeType(f);
		UploadResult uploadResult = sc.upload(filename, f, c.getIdString());

		if (item == null)
			item = ImejiFactory.newItem(c);
		
		if (filename == null || filename.equals("")) {
			throw new UnprocessableError("Filename or reference must not be empty!");
		}
		
		item = ImejiFactory.newItem(item, c, user, uploadResult.getId(),
				filename, URI.create(uploadResult.getOrginal()),
				URI.create(uploadResult.getThumb()),
				URI.create(uploadResult.getWeb()), mimeType);
		item.setChecksum(uploadResult.getChecksum());
		return create(item, c.getId(), user);
	}

	/**
	 * Create an {@link Item} with an external {@link File} according to its URL
	 * 
	 * @param item
	 * @param c
	 * @param externalFileUrl
	 * @param download
	 * @param user
	 * @return
	 * @throws IOException 
	 * @throws ImejiException
	 */
	public Item createWithExternalFile(Item item, CollectionImeji c,
			String externalFileUrl, String filename, boolean download, User user)
			throws ImejiException {
		File tmp = null;
		String origName = FilenameUtils.getName(externalFileUrl);
		if ("".equals(filename) || filename == null)
			filename = origName;
		else
			filename = filename + "." + FilenameUtils.getExtension(origName);
		
		if (filename == null || filename.equals("")) {
			throw new UnprocessableError("Filename or reference must not be empty!");
		}
		
		StorageController sController = new StorageController("external");
		if (item == null)
			item = ImejiFactory.newItem(c);
		if (download) {
			// download the file in storage
			try {
			tmp = File.createTempFile("imeji", null);
			sController.read(externalFileUrl, new FileOutputStream(tmp), true);
			}
			catch (Exception e) {
//				throw new UnprocessableError("There has been a problem with the file upload. ");
				throw new UnprocessableError(e.getLocalizedMessage());
			}
			item = createWithFile(item, tmp, filename, c, user);
		} else {
			// Reference the file
			item.setFilename(filename);
			item.setFullImageUrl(URI.create(externalFileUrl));
			item.setThumbnailImageUrl(URI.create(NO_THUMBNAIL_URL));
			item.setWebImageUrl(URI.create(NO_THUMBNAIL_URL));
			item = create(item, c.getId(), user);
		}
		return item;
	}

	/**
	 * Create a {@link List} of {@link Item} in a {@link CollectionImeji}. This
	 * method is faster than using create(Item item, URI coll) when creating
	 * many items
	 * 
	 * @param items
	 * @param coll
	 * @throws ImejiException
	 */
	public void create(Collection<Item> items, URI coll, User user)
			throws ImejiException {
		CollectionController cc = new CollectionController();
		CollectionImeji ic = cc.retrieve(coll, user);
		for (Item img : items) {
			writeCreateProperties(img, user);
			if (Status.PENDING.equals(ic.getStatus())) {
				img.setVisibility(Visibility.PRIVATE);
			} else {
				img.setVisibility(Visibility.PUBLIC);
			}
			img.setCollection(coll);
			img.getMetadataSet().setProfile(ic.getProfile());
			ic.getImages().add(img.getId());
		}
		writer.create(J2JHelper.cast2ObjectList(new ArrayList<Item>(items)),
				user);
		cc.update(ic, user);

	}
	
	public Item create(Item item, File uploadedFile, String filename, User u, String fetchUrl, String referenceUrl) throws ImejiException 
	{
		
		if (u == null) {
			throw new AuthenticationError(CommonUtils.USER_MUST_BE_LOGGED_IN);
		} 

		if (filename == null || filename.isEmpty()) {
			throw new BadRequestException("Filename or reference must not be empty!");
		}
		Item newItem = new Item(item); 
		CollectionController cc = new CollectionController();
		CollectionImeji collection;
		try {
			collection = cc.retrieve(item.getCollection(), u);
			if (collection.getStatus().equals(Status.WITHDRAWN)) {
				throw new UnprocessableError("Collection is withdrawn, you can not create an item.");
			}
		}
		catch (Exception e)
		{
			throw new UnprocessableError("There was a problem with specified collection. Check the collectionID for correctness!");
		}
		
		if (uploadedFile != null ) {
			newItem = createWithFile (item, uploadedFile, filename, collection, u );
		} else if (getExternalFileUrl(fetchUrl, referenceUrl) != null) {
			// If no file, but either a fetchUrl or a referenceUrl
			newItem = createWithExternalFile(item, collection,
					getExternalFileUrl(fetchUrl, referenceUrl), filename,
					downloadFile(fetchUrl), u);
		}
		else
		{
			throw new BadRequestException("Filename or reference must not be empty!");
		}

		return newItem;
	}

	/**
	 * User ObjectLoader to load image
	 * 
	 * @param imgUri
	 * @return
	 * @throws ImejiException
	 */
	public Item retrieve(URI imgUri, User user) throws ImejiException {
		return (Item) reader.read(imgUri.toString(), user, new Item());
	}

	/**
	 * Load the {@link List} of {@link Item}
	 * 
	 * @param uris
	 * @param limit
	 * @param offset
	 * @return
	 */
	public Collection<Item> retrieve(List<String> uris, int limit, int offset,
			User user) {
		int counter = 0;
		List<Item> items = new ArrayList<Item>();
		for (String s : uris) {
			if (offset <= counter
					&& (counter < (limit + offset) || limit == -1)) {
				items.add((Item) J2JHelper.setId(new Item(), URI.create(s)));
			}
			counter++;
		}
		try {
			reader.read(J2JHelper.cast2ObjectList(items), user);
			return items;
		} catch (Exception e) {
			throw new RuntimeException(
					"Error loading items: " + e.getMessage(), e);
		}
	}

	/**
	 * Retrieve all {@link Item} (all status, all users) in imeji
	 * 
	 * @return
	 */
	public Collection<Item> retrieveAll(User user) {
		List<String> uris = ImejiSPARQL.exec(SPARQLQueries.selectItemAll(),
				Imeji.imageModel);
		return retrieve(uris, -1, 0, user);
	}

	/**
	 * Update an {@link Item} in the database
	 * 
	 * @param item
	 * @param user
	 * @throws ImejiException
	 */
	public Item update(Item item, User user) throws ImejiException {
		Collection<Item> l = new ArrayList<Item>();
		l.add(item);
		update(l, user);
		return retrieve(item.getId(), user);
	}

	/**
	 * Update a {@link Collection} of {@link Item}
	 * 
	 * @param items
	 * @param user
	 * @throws ImejiException
	 */
	public void update(Collection<Item> items, User user) throws ImejiException {
		List<Object> imBeans = new ArrayList<Object>();
		for (Item item : items) {

			writeUpdateProperties(item, user);
			imBeans.add(createFulltextForMetadata(item));
		}
		writer.update(imBeans, user);
	}

	/**
	 * Update the File of an {@link Item}
	 * 
	 * @param item
	 * @param f
	 * @param user
	 * @return
	 * @throws ImejiException
	 */
	public Item updateFile(Item item, File f, User user) throws ImejiException {

		//First remove the old File from the Internal Storage if its there
		if (!isNullOrEmpty(item.getStorageId())) {
			removeFileFromStorage(item.getStorageId());
		}
		
		CollectionController c = new CollectionController();
		
		StorageController sc = new StorageController();
		UploadResult uploadResult = sc.upload(item.getFilename(), f, c.retrieve(item.getCollection(), user).getIdString());

		item.setFiletype(getMimeType(f));
		item.setChecksum(calculateChecksum(f));
		item.setStorageId(uploadResult.getId());
		item.setFullImageUrl(URI.create(uploadResult.getOrginal()));
		item.setThumbnailImageUrl(URI.create(uploadResult.getThumb()));
		item.setWebImageUrl(URI.create(uploadResult.getWeb()));
		//item.setFilename(uploadResult.)
		
/*		InternalStorageManager ism = new InternalStorageManager();

		//regenerate new url!!!
		String url = ism.generateUrl(item.getStorageId(), f.getName(), FileResolution.ORIGINAL);
		sc.update(url, f);
		item.setFullImageUrl(URI.create(url));

		url = ism.generateUrl(item.getStorageId(), f.getName(), FileResolution.WEB);
		sc.update(url, f);
		item.setWebImageUrl(URI.create(url));

		url = ism.generateUrl(item.getStorageId(), f.getName(), FileResolution.THUMBNAIL);
		sc.update(url, f);
		item.setThumbnailImageUrl(URI.create(url));
*/		
	

		return update(item, user);
	}

	/**
	 * Update the {@link Item} with External link to File.
	 *
	 * @param item
	 * @param externalFileUrl
	 * @param filename
	 * @param download
	 * @param u
	 * @return
	 * @throws ImejiException
	 */
	public Item updateWithExternalFile(Item item, String externalFileUrl,
			String filename, boolean download, User u) throws ImejiException {
		String origName = FilenameUtils.getName(externalFileUrl);
		filename = isNullOrEmpty(filename) ? origName : filename + "."
				+ FilenameUtils.getExtension(origName);

		item.setFilename(filename);

		StorageController sc = new StorageController("external");
		if (download) {
			// download the file in storage
			try {
			File tmp = File.createTempFile("imeji", "." + FilenameUtils.getExtension(origName));
			sc.read(externalFileUrl, new FileOutputStream(tmp), true);
			item = updateFile(item, tmp, u);
			}
			catch (Exception e)
			{
				throw new UnprocessableError("There was a problem with file update");
			}
		} else {
			
			removeFileFromStorage(item.getStorageId());
			// Reference the file
			item.setFullImageUrl(URI.create(externalFileUrl));
			item.setThumbnailImageUrl(URI.create(NO_THUMBNAIL_URL));
			item.setWebImageUrl(URI.create(NO_THUMBNAIL_URL));
			item.setChecksum("");
			item.setFiletype("");
			item.setFilename("");

			item = update(item, u);
		}
		return item;

	}

	/**
	 * 
	 * Update only the thumbnail and the Web Resolution (doesn't change the
	 * original file)
	 * 
	 * @param item
	 * @param f
	 * @param user
	 * @return
	 * @throws ImejiException
	 */
	public Item updateThumbnail(Item item, File f, User user) throws ImejiException {
		StorageController sc = new StorageController();
		sc.update(item.getWebImageUrl().toString(), f);
		sc.update(item.getThumbnailImageUrl().toString(), f);
		return update(item, user);
	}

	/**
	 * Delete a {@link List} of {@link Item} inclusive all files stored in the
	 * {@link Storage}
	 * 
	 * @param items
	 * @param user
	 * @return
	 * @throws ImejiException
	 */
	public int delete(List<Item> items, User user) throws ImejiException {
		int count = 0;
		Map<String, URI> cMap = new HashMap<String, URI>();
		List<Object> toDelete = new ArrayList<Object>();
		for (Item item : items) {
			if (item != null) {
				removeFileFromStorage(item.getStorageId());
				toDelete.add(item);
				count++;
				cMap.put(item.getCollection().toString(), item.getCollection());
			}
		}
		writer.delete(toDelete, user);
		return count;
	}

	/**
	 * Delete a {@link List} of {@link Item} inclusive all files stored in the
	 * {@link Storage}
	 * 
	 * @param items
	 * @param user
	 * @return
	 * @throws ImejiException
	 */
	public int delete(String itemId, User u) throws ImejiException {
		
		if (u == null ) {
			throw new AuthenticationError(CommonUtils.USER_MUST_BE_LOGGED_IN);
		}
		
		Item item= retrieve(ObjectHelper.getURI(Item.class, itemId), u);
		
		if (!Status.PENDING.equals(item.getStatus())) {
			throw new UnprocessableError("Item status must be PENDING.");
		}
		
		List<Item> items = new ArrayList<Item>();
		items.add(item);
		return (delete(items, u));
	}
	/**
	 * Search {@link Item}
	 * 
	 * @param containerUri
	 *            - if the search is done within a {@link Container}
	 * @param searchQuery
	 *            - the {@link SearchQuery}
	 * @param sortCri
	 *            - the {@link SortCriterion}
	 * @param uris
	 *            - The {@link List} of uri to restrict the search
	 * @param user
	 * @return
	 */
	public SearchResult search(URI containerUri, SearchQuery searchQuery,
			SortCriterion sortCri, List<String> uris, User user) {
		String uriString = containerUri != null ? containerUri.toString()
				: null;
		Search search = SearchFactory.create(SearchType.ITEM, uriString);
		return search.search(searchQuery, sortCri, user, uris);
	}

	/**
	 * load items of a container. Perform a search to load all items: is faster
	 * than to read the complete container
	 * 
	 * @param c
	 * @param user
	 */
	public Container searchAndSetContainerItems(Container c, User user,
			int limit, int offset) {
		ItemController ic = new ItemController();
		List<String> newUris = ic.search(c.getId(), null, null, null, user)
				.getResults();
		c.getImages().clear();
		for (String s : newUris) {
			c.getImages().add(URI.create(s));
		}
		return c;
	}

	/**
	 * Load items from a {@link Container} without any ordering. This is faster
	 * than searchAndSetContainerItems Method, but is working only with tdb
	 * 
	 * @param c
	 * @param size
	 * @return
	 */
	public Container searchAndSetContainerItemsFast(Container c, User user,
			int size) {
		String q = c instanceof CollectionImeji ? SPARQLQueries
				.selectCollectionItems(c.getId(), user, size) : SPARQLQueries
				.selectAlbumItems(c.getId(), user, size);
		c.getImages().clear();
		for (String s : ImejiSPARQL.exec(q, null)) {
			c.getImages().add(URI.create(s));
		}
		return c;
	}

	/**
	 * Set the status of a {@link List} of {@link Item} to released
	 * 
	 * @param l
	 * @param user
	 * @throws ImejiException
	 */
	public void release(List<Item> l, User user) throws ImejiException {
		for (Item item : l) {
			if (Status.PENDING.equals(item.getStatus())) {
				writeReleaseProperty(item, user);
				item.setVisibility(Visibility.PUBLIC);
			}
		}
		update(l, user);
	}

	/**
	 * Make the Items private
	 * 
	 * @param l
	 * @param user
	 * @throws ImejiException
	 */
	public void unRelease(List<Item> l, User user) throws ImejiException {
		for (Item item : l) {
			item.setStatus(Status.PENDING);
		}
		update(l, user);
	}

	/**
	 * Set the status of a {@link List} of {@link Item} to withdraw and delete
	 * its files from the {@link Storage}
	 * 
	 * @param items
	 * @param comment
	 * @throws ImejiException
	 */
	public void withdraw(List<Item> items, String comment, User user)
			throws ImejiException {
		Map<String, URI> cMap = new HashMap<String, URI>();
		for (Item item : items) {
			if (!item.getStatus().equals(Status.RELEASED)) {
				throw new RuntimeException("Error discard " + item.getId()
						+ " must be release (found: " + item.getStatus() + ")");
			} else {
				writeWithdrawProperties(item, comment);
				item.setVisibility(Visibility.PUBLIC);
				if (item.getEscidocId() != null) {
					removeFileFromStorage(item.getStorageId());
					item.setEscidocId(null);
				}
			}
		}
		update(items, user);
		// Remove items from their collections
		for (URI uri : cMap.values()) {
			CollectionController cc = new CollectionController();
			CollectionImeji c = cc.retrieveLazy(uri, user);
			c = (CollectionImeji) searchAndSetContainerItems(c, user, -1, 0);
			cc.update(c, user);
		}
	}

	/**
	 * Return the size of a {@link Container}
	 * 
	 * @param c
	 * @return
	 */
	public int countContainerSize(Container c) {
		String q = c instanceof CollectionImeji ? SPARQLQueries
				.countCollectionSize(c.getId()) : SPARQLQueries
				.countAlbumSize(c.getId());
		return ImejiSPARQL.execCount(q, null);
	}

	/**
	 * Remove a file from the current {@link Storage}
	 * 
	 * @param id
	 */
	private void removeFileFromStorage(String id) {
		StorageController storageController = new StorageController();
		try {
			storageController.delete(id);
		} catch (Exception e) {
			logger.error("error deleting file", e);
		}
	}

	/**
	 * Initialize the fulltext search value for all {@link Metadata} of an
	 * {@link Item}
	 * 
	 * @param item
	 * @return
	 */
	private Item createFulltextForMetadata(Item item) {
		for (MetadataSet mds : item.getMetadataSets()) {
			for (Metadata md : mds.getMetadata()) {
				md.asFulltext();
			}
		}
		return item;
	}
	
	
	/**
	 * Return the external Url of the File 
	 * 
	 * @param to
	 * @return
	 */
	private String getExternalFileUrl(String fetchUrl, String referenceUrl) {
		return firstNonNullOrEmtpy(fetchUrl, referenceUrl);
	}

	private String firstNonNullOrEmtpy(String... strs) {
		if (strs == null)
			return null;
		for (String str : strs)
			if (str != null && !"".equals(str.trim()))
				return str;
		return null;
	}

	/**
	 * True if the file must be download in imeji (i.e fetchurl is defined)
	 * 
	 * @param to
	 * @return
	 */
	private boolean downloadFile(String fetchUrl) {
		return !isNullOrEmpty(fetchUrl);
	}


}
