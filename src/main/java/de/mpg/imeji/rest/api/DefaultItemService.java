package de.mpg.imeji.rest.api;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.mpg.imeji.rest.process.ReverseTransferObjectFactory.TRANSFER_MODE.CREATE;
import static de.mpg.imeji.rest.process.ReverseTransferObjectFactory.TRANSFER_MODE.UPDATE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import de.mpg.imeji.exceptions.BadRequestException;
import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.controller.ItemController;
import de.mpg.imeji.logic.controller.ProfileController;
import de.mpg.imeji.logic.search.SearchFactory;
import de.mpg.imeji.logic.search.SearchFactory.SEARCH_IMPLEMENTATIONS;
import de.mpg.imeji.logic.search.SearchQueryParser;
import de.mpg.imeji.logic.search.SearchResult;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.util.StringHelper;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.MetadataProfile;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.rest.helper.ProfileCache;
import de.mpg.imeji.rest.process.ReverseTransferObjectFactory;
import de.mpg.imeji.rest.process.TransferObjectFactory;
import de.mpg.imeji.rest.to.MetadataProfileTO;
import de.mpg.imeji.rest.to.SearchResultTO;
import de.mpg.imeji.rest.to.defaultItemTO.DefaultItemTO;
import de.mpg.imeji.rest.to.defaultItemTO.DefaultItemWithFileTO;

public class DefaultItemService implements API<DefaultItemTO> {
  private final ItemController controller = new ItemController();

  @Override
  public DefaultItemTO create(DefaultItemTO to, User u) throws ImejiException {
    if (to instanceof DefaultItemWithFileTO) {
      // get newFilename
      String filename = getFilename((DefaultItemWithFileTO) to);
      // Get Item profile
      MetadataProfile profile = getProfile(to, u);
      // transfer TO into item
      Item item = new Item();
      ReverseTransferObjectFactory.transferDefaultItem(to, item, profile, u, CREATE);
      item = controller.create(item, ((DefaultItemWithFileTO) to).getFile(), filename, u,
          ((DefaultItemWithFileTO) to).getFetchUrl(),
          ((DefaultItemWithFileTO) to).getReferenceUrl());
      // transfer item into ItemTO
      to = new DefaultItemTO();
      TransferObjectFactory.transferDefaultItem(item, to, profile);
      return to;
    } else {
      throw new BadRequestException(
          "A file must be uploaded, referenced or fetched from external location.");
    }
  }

  @Override
  public DefaultItemTO read(String id, User u) throws ImejiException {
    DefaultItemTO defaultTO = new DefaultItemTO();
    Item item = controller.retrieve(ObjectHelper.getURI(Item.class, id), u);
    ProfileCache profileCache = new ProfileCache();
    TransferObjectFactory.transferDefaultItem(item, defaultTO,
        profileCache.read(item.getMetadataSet().getProfile()));
    return defaultTO;
  }

  @Override
  public DefaultItemTO update(DefaultItemTO to, User u) throws ImejiException {
    Item item = controller.retrieve(ObjectHelper.getURI(Item.class, to.getId()), u);
    // Get Item profile
    MetadataProfile profile = getProfile(to, u);
    // Transfer the item
    ReverseTransferObjectFactory.transferDefaultItem(to, item, profile, u, UPDATE);
    DefaultItemWithFileTO tof = (DefaultItemWithFileTO) to;
    String url = getExternalFileUrl(tof);
    if (tof.getFile() != null) {
      item = controller.updateFile(item, tof.getFile(), to.getFilename(), u);
    } else if (!StringHelper.isNullOrEmptyTrim(url)) {
      item = controller.updateWithExternalFile(item, getExternalFileUrl(tof), to.getFilename(),
          !isNullOrEmpty(tof.getFetchUrl()), u);
    } else {
      item = controller.update(item, u);
    }
    to = new DefaultItemTO();
    TransferObjectFactory.transferDefaultItem(item, to, profile);
    return to;
  }

  @Override
  public boolean delete(String id, User u) throws ImejiException {
    controller.delete(id, u);
    return true;
  }

  @Override
  public DefaultItemTO release(String i, User u) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DefaultItemTO withdraw(String i, User u, String discardComment) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void share(String id, String userId, List<String> roles, User u) throws ImejiException {
    // TODO Auto-generated method stub

  }

  @Override
  public void unshare(String id, String userId, List<String> roles, User u) throws ImejiException {
    // TODO Auto-generated method stub

  }

  @Override
  public List<String> search(String q, User u) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Search and retrieve items according ton the query.
   * 
   * @param u
   * @param q
   * @param offset
   * @param size
   * @return
   * @throws ImejiException
   * @throws IOException
   */
  public SearchResultTO<DefaultItemTO> searchAndRetrieveItems(User u, String q, int offset,
      int size) throws ImejiException, IOException {
    ProfileCache profileCache = new ProfileCache();
    List<DefaultItemTO> tos = new ArrayList<>();
    ItemController controller = new ItemController();
    SearchResult result = SearchFactory.create(SEARCH_IMPLEMENTATIONS.ELASTIC)
        .search(SearchQueryParser.parseStringQuery(q), null, u, null, null, offset, size);
    for (Item vo : controller.retrieveBatch(result.getResults(), -1, 0, u)) {
      DefaultItemTO to = new DefaultItemTO();
      TransferObjectFactory.transferDefaultItem(vo, to,
          profileCache.read(vo.getMetadataSet().getProfile()));
      tos.add(to);
    }
    return new SearchResultTO.Builder<DefaultItemTO>().numberOfRecords(result.getResults().size())
        .offset(offset).results(tos).query(q).size(size)
        .totalNumberOfRecords(result.getNumberOfRecords()).build();
  }

  /**
   * Get the {@link MetadataProfileTO} of the {@link DefaultItemTO}
   * 
   * @param to
   * @param u
   * @return
   * @throws ImejiException
   */
  private MetadataProfile getProfile(DefaultItemTO to, User u) throws ImejiException {
    if (to.getCollectionId() != null) {
      return new ProfileController().retrieveByCollectionId(
          ObjectHelper.getURI(CollectionImeji.class, to.getCollectionId()), u);
    } else {
      return new ProfileController().retrieveByItemId(ObjectHelper.getURI(Item.class, to.getId()),
          u);
    }
  }

  /**
   * Find the correct filename if there
   * 
   * @param to
   * @return
   **/
  public String getFilename(DefaultItemWithFileTO to) {
    return firstNonNullOrEmtpy(to.getFilename(),
        (to.getFile() != null) ? FilenameUtils.getName(to.getFile().getName()) : "",
        FilenameUtils.getName(to.getFetchUrl()), FilenameUtils.getName(to.getReferenceUrl()));
  }

  /**
   * Return the external Url
   * 
   * @param to
   * @return
   */
  private String getExternalFileUrl(DefaultItemWithFileTO to) {
    return firstNonNullOrEmtpy(to.getFetchUrl(), to.getReferenceUrl());
  }

  private String firstNonNullOrEmtpy(String... strs) {
    if (strs == null)
      return null;
    for (String str : strs)
      if (str != null && !"".equals(str.trim()))
        return str;
    return null;
  }
}