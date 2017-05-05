package de.mpg.imeji.presentation.statement;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;

import org.apache.log4j.Logger;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.exceptions.UnprocessableError;
import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.item.ItemService;
import de.mpg.imeji.logic.search.factory.SearchFactory;
import de.mpg.imeji.logic.search.model.SearchFields;
import de.mpg.imeji.logic.search.model.SearchLogicalRelation.LOGICAL_RELATIONS;
import de.mpg.imeji.logic.search.model.SearchPair;
import de.mpg.imeji.logic.statement.StatementService;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.util.UrlHelper;
import de.mpg.imeji.logic.vo.Statement;
import de.mpg.imeji.presentation.session.BeanHelper;

/**
 * JSF Bean for the page edit statement
 * 
 * @author saquet
 *
 */
@ViewScoped
@ManagedBean(name = "StatementEditBean")
public class StatementEditBean extends StatementCreateBean {
  private static final long serialVersionUID = 5191523522987113715L;
  private static final Logger LOGGER = Logger.getLogger(StatementEditBean.class);
  private StatementService service = new StatementService();
  private boolean used = false;
  private Statement statement;

  public StatementEditBean() {

  }

  @PostConstruct
  public void init() {
    try {
      String id = URLDecoder.decode(UrlHelper.getParameterValue("statementId"), "UTF-8");
      statement =
          service.retrieve(ObjectHelper.getURI(Statement.class, id).toString(), getSessionUser());
      getStatementForm().setType(statement.getType().name());
      getStatementForm().setName(statement.getIndex());
      getStatementForm().setNamespace(statement.getNamespace());
      if (statement.getVocabulary() != null) {
        getStatementForm().setUseGoogleMapsAPI(
            statement.getVocabulary().toString().equals(Imeji.CONFIG.getGoogleMapsApi()));
        getStatementForm().setUseMaxPlanckAuthors(
            statement.getVocabulary().toString().equals(Imeji.CONFIG.getConeAuthors()));
      }
      if (statement.getLiteralConstraints() != null) {
        getStatementForm().getPredefinedValues().addAll(statement.getLiteralConstraints());
      }
      used = searchIfUsed(statement);
    } catch (ImejiException | UnsupportedEncodingException e) {
      LOGGER.error("Error retrieving statement: ", e);
    }
  }

  /**
   * True if the Statement is used by at least one item
   * 
   * @param s
   * @return
   * @throws UnprocessableError
   */
  private boolean searchIfUsed(Statement s) throws UnprocessableError {
    SearchFactory factory = new SearchFactory();
    factory.addElement(new SearchPair(SearchFields.index, s.getIndexUrlEncoded()),
        LOGICAL_RELATIONS.AND);
    return new ItemService().search(factory.build(), null, Imeji.adminUser, 0, 1)
        .getNumberOfRecords() > 0;
  }

  @Override
  public void save() {
    try {
      service.update(statement, getStatementForm().asStatement(), getSessionUser());
      redirect(getNavigation().getApplicationUrl() + "statements");
    } catch (final ImejiException | IOException e) {
      BeanHelper.error("Error editing statement: " + e.getMessage());
      LOGGER.error("Error editing statement", e);
    }
  }

  /**
   * @return the used
   */
  public boolean isUsed() {
    return used;
  }

  /**
   * @param used the used to set
   */
  public void setUsed(boolean used) {
    this.used = used;
  }

  public Statement getStatement() {
    return statement;
  }
}
