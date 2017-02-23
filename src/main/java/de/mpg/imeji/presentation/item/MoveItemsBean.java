package de.mpg.imeji.presentation.item;

import static de.mpg.imeji.logic.search.model.SearchLogicalRelation.LOGICAL_RELATIONS.AND;
import static de.mpg.imeji.logic.search.model.SearchLogicalRelation.LOGICAL_RELATIONS.OR;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;

import org.apache.log4j.Logger;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.authorization.Authorization;
import de.mpg.imeji.logic.collection.CollectionService;
import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.item.ItemService;
import de.mpg.imeji.logic.search.factory.SearchFactory;
import de.mpg.imeji.logic.search.model.SearchFields;
import de.mpg.imeji.logic.search.model.SearchPair;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.Grant.GrantType;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.presentation.beans.SuperBean;
import de.mpg.imeji.presentation.session.BeanHelper;
import de.mpg.imeji.presentation.session.SessionBean;

@ManagedBean(name = "MoveItemsBean")
@ViewScoped
public class MoveItemsBean extends SuperBean {
  private static final long serialVersionUID = 2230148128355260199L;
  private static final Logger LOGGER = Logger.getLogger(MoveItemsBean.class);
  private List<CollectionImeji> collectionsForMove = new ArrayList<>();
  @ManagedProperty(value = "#{SessionBean}")
  private SessionBean sessionBean;
  private String query = "";

  /**
   * Load all the collection for which the user has at least edit role
   * 
   * @throws ImejiException
   */
  public void searchCollectionsForMove(String collectionSrcId) throws ImejiException {
    SearchFactory factory = new SearchFactory();
    factory.addElement(new SearchPair(SearchFields.title, query + "*"), OR);
    if (!new Authorization().isSysAdmin(getSessionUser())) {
      factory.addElement(new SearchPair(SearchFields.role, GrantType.EDIT.name().toLowerCase()),
          AND);
    }
    collectionsForMove = new CollectionService()
        .searchAndRetrieve(factory.build(), null, getSessionUser(), -1, 0).stream()
        .filter(c -> !c.getId().toString().equals(collectionSrcId)).collect(Collectors.toList());
  }

  /**
   * Move the selected items to the collection
   * 
   * @param collectionId
   * @throws IOException
   */
  public void moveSelectedTo(String collectionId) throws IOException {
    moveTo(collectionId, sessionBean.getSelected());
    sessionBean.getSelected().clear();
    redirect(getHistory().getCurrentPage().getCompleteUrl());
  }

  /**
   * Move a single Item to the collection
   * 
   * @param collectionId
   * @param id
   * @throws IOException
   */
  public void moveItemTo(String collectionId, String id) throws IOException {
    moveTo(collectionId, Arrays.asList(id));
    redirect(getNavigation().getCollectionUrl() + ObjectHelper.getId(URI.create(collectionId))
        + "/item/" + ObjectHelper.getId(URI.create(id)));
  }

  /**
   * Move the items (defined by a list of their ids) to the collection
   * 
   * @param collectionId
   * @param ids
   */
  public void moveTo(String collectionId, List<String> ids) {
    try {
      CollectionImeji col = collectionsForMove.stream()
          .filter(c -> c.getId().toString().equals(collectionId)).findAny().get();
      List<Item> moved = new ItemService().moveItems(ids, col, getSessionUser());
      if (moved.size() > 0) {
        BeanHelper.addMessage(moved.size() + " "
            + (moved.size() > 1 ? Imeji.RESOURCE_BUNDLE.getLabel("items", getLocale())
                : Imeji.RESOURCE_BUNDLE.getLabel("item", getLocale()))
            + " " + Imeji.RESOURCE_BUNDLE.getLabel("moved", getLocale()));
      }
      int notMoved = ids.size() - moved.size();
      if (notMoved > 0) {
        BeanHelper.error(notMoved + " "
            + (notMoved > 1 ? Imeji.RESOURCE_BUNDLE.getLabel("items", getLocale())
                : Imeji.RESOURCE_BUNDLE.getLabel("item", getLocale()))
            + " " + Imeji.RESOURCE_BUNDLE.getLabel("moved_error", getLocale()));
      }

    } catch (Exception e) {
      BeanHelper.error("Error moving items " + e.getMessage());
      LOGGER.error("Error moving items ", e);
    }
  }


  /**
   * List of all Collection where the user could move items into
   * 
   * @return
   */
  public List<CollectionImeji> getCollectionsForMove() {
    return collectionsForMove;
  }

  public SessionBean getSessionBean() {
    return sessionBean;
  }

  public void setSessionBean(SessionBean sessionBean) {
    this.sessionBean = sessionBean;
  }

  /**
   * @return the query
   */
  public String getQuery() {
    return query;
  }

  /**
   * @param query the query to set
   */
  public void setQuery(String query) {
    this.query = query;
  }
}