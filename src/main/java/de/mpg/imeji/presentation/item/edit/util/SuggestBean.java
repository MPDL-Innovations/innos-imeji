package de.mpg.imeji.presentation.item.edit.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.model.SelectItem;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.core.statement.StatementService;
import de.mpg.imeji.logic.model.Statement;
import de.mpg.imeji.logic.search.Search;
import de.mpg.imeji.presentation.util.CommonUtils;

/**
 * JSF Bean for the the auto suggest feature <br/>
 * - Important: now the auto suggest for External vocabulary is done with jQuery and the Servlet
 * "autocompleter"
 *
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
@ManagedBean(name = "SuggestBean")
@ViewScoped
public class SuggestBean {
  /**
   * The current {@link Suggest}
   */
  private Map<URI, Suggest> suggests = null;

  /**
   * Initialize the bean for one profile
   *
   * @param profile
   */
  public void init() {
    suggests = new HashMap<URI, Suggest>();
    try {
      for (final Statement s : new StatementService().searchAndRetrieve(null, null, Imeji.adminUser, Search.GET_ALL_RESULTS,
          Search.SEARCH_FROM_START_INDEX)) {
        suggests.put(s.getUri(), new Suggest(s));
      }
    } catch (final ImejiException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  /**
   * getter
   *
   * @return
   */
  public Map<URI, Suggest> getSuggests() {
    return suggests;
  }

  /**
   * setter
   *
   * @param suggests
   */
  public void setSuggests(Map<URI, Suggest> suggests) {
    this.suggests = suggests;
  }

  /**
   * Contains suggestions
   *
   * @author saquet (initial creation)
   * @author $Author$ (last modification)
   * @version $Revision$ $LastChangedDate$
   */
  public class Suggest {
    /**
     * The current statement for which the Suggestion will be done
     */
    private Statement statement = null;

    /**
     * Constructor
     *
     * @param s
     */
    public Suggest(Statement s) {
      statement = s;
    }

    /**
     * Return the list of restricted values as a menu
     *
     * @return
     */
    public List<SelectItem> getRestrictedValues() {
      if (statement.getLiteralConstraints() != null && statement.getLiteralConstraints().size() > 0) {
        final List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem(null, "-"));
        for (final String str : statement.getLiteralConstraints()) {
          list.add(new SelectItem(str, CommonUtils.extractFieldValue("name", str)));
        }
        return list;
      }
      return null;
    }

    /**
     * Return true if the current {@link Statement} has restricted values
     *
     * @return
     */
    public boolean getHasRestrictedValues() {
      if (statement != null) {
        if (statement.getLiteralConstraints() != null && statement.getLiteralConstraints().size() > 0) {
          return true;
        }
      }
      return false;
    }
  }
}
