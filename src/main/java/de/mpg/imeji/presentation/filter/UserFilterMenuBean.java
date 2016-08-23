package de.mpg.imeji.presentation.filter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.model.SelectItem;

import org.apache.log4j.Logger;

import de.mpg.imeji.exceptions.UnprocessableError;
import de.mpg.imeji.logic.Imeji;
import de.mpg.imeji.logic.search.SearchQueryParser;
import de.mpg.imeji.logic.search.model.SearchIndex.SearchFields;
import de.mpg.imeji.logic.search.model.SearchOperators;
import de.mpg.imeji.logic.search.model.SearchPair;
import de.mpg.imeji.logic.search.model.SearchQuery;

@ManagedBean(name = "UserFilterMenuBean")
@ViewScoped
public class UserFilterMenuBean extends SuperFilterMenuBean {
  private static final long serialVersionUID = -3783528849872530224L;
  private static final Logger LOGGER = Logger.getLogger(UserFilterMenuBean.class);

  @PostConstruct
  public void init() {
    if (getSessionUser() != null) {
      try {
        init(initMenu());
      } catch (UnprocessableError e) {
        LOGGER.error("Error initializing UserFilterMenuBean", e);
      }
    }
  }

  /**
   * Initialize the menus
   * 
   * @throws UnprocessableError
   */
  private List<SelectItem> initMenu() throws UnprocessableError {
    List<SelectItem> menu = new ArrayList<SelectItem>();
    menu.add(new SelectItem(
        SearchQueryParser
            .transform2URL(SearchQuery.toSearchQuery(new SearchPair(SearchFields.creator,
                SearchOperators.REGEX, getSessionUser().getEmail(), false))),
        Imeji.RESOURCE_BUNDLE.getLabel("created_by_me", getLocale())));
    menu.add(new SelectItem(
        SearchQueryParser
            .transform2URL(SearchQuery.toSearchQuery(new SearchPair(SearchFields.collaborator,
                SearchOperators.REGEX, getSessionUser().getEmail(), false))),
        Imeji.RESOURCE_BUNDLE.getLabel("shared_with_me", getLocale())));
    menu.add(new SelectItem("createdOrShared",
        Imeji.RESOURCE_BUNDLE.getLabel("created_or_shared_with_me", getLocale())));
    return menu;
  }
}