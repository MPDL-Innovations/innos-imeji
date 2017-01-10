/**
 * License: src/main/resources/license/escidoc.license
 */
package de.mpg.imeji.logic.controller;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import de.mpg.imeji.exceptions.AuthenticationError;
import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.exceptions.NotSupportedMethodException;
import de.mpg.imeji.exceptions.UnprocessableError;
import de.mpg.imeji.exceptions.WorkflowException;
import de.mpg.imeji.logic.Imeji;
import de.mpg.imeji.logic.authorization.AuthorizationPredefinedRoles;
import de.mpg.imeji.logic.concurrency.locks.Locks;
import de.mpg.imeji.logic.config.ImejiLicenses;
import de.mpg.imeji.logic.storage.Storage.FileResolution;
import de.mpg.imeji.logic.storage.internal.InternalStorageManager;
import de.mpg.imeji.logic.user.UserController;
import de.mpg.imeji.logic.util.StringHelper;
import de.mpg.imeji.logic.vo.Album;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.Container;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.License;
import de.mpg.imeji.logic.vo.Properties;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.logic.workflow.WorkflowManager;

/**
 * Abstract class for the controller in imeji dealing with imeji VO: {@link Item}
 * {@link CollectionImeji} {@link Album} {@link User} {@link MetadataProfile}
 *
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
public abstract class ImejiController {
  private static final WorkflowManager WORKFLOW_MANAGER = new WorkflowManager();

  /**
   * If a user is not logged in, throw a Exception
   *
   * @param user
   * @throws AuthenticationError
   */
  protected void isLoggedInUser(User user) throws AuthenticationError {
    if (user == null) {
      throw new AuthenticationError(AuthenticationError.USER_MUST_BE_LOGGED_IN);
    }
  }

  /**
   * Add the {@link Properties} to an imeji object when it is created
   *
   * @param properties
   * @param user
   * @throws WorkflowException
   */
  protected void prepareCreate(Properties properties, User user) throws WorkflowException {
    WORKFLOW_MANAGER.prepareCreate(properties, user);
  }

  /**
   * Update the grants of the user who created the objects
   * 
   * @param user
   * @param uri
   * @throws ImejiException
   */
  protected void updateCreatorGrants(User user, String uri) throws ImejiException {
    user.getGrants().addAll(AuthorizationPredefinedRoles.admin(uri));
    new UserController().update(user);
  }

  /**
   * Add the {@link Properties} to an imeji object when it is updated
   *
   * @param properties
   * @param user
   */
  protected void prepareUpdate(Properties properties, User user) {
    WORKFLOW_MANAGER.prepareUpdate(properties, user);
  }

  /**
   * Add the {@link Properties} to an imeji object when it is released
   *
   * @param properties
   * @param user
   * @throws WorkflowException
   * @throws NotSupportedMethodException
   */
  protected void prepareRelease(Properties properties, User user)
      throws WorkflowException, NotSupportedMethodException {
    WORKFLOW_MANAGER.prepareRelease(properties);
  }

  /**
   * Add the {@link Properties} to an imeji object when it is withdrawn
   *
   * @param properties
   * @param comment
   * @throws WorkflowException
   * @throws NotSupportedMethodException
   * @throws UnprocessableError
   */
  protected void prepareWithdraw(Properties properties, String comment)
      throws WorkflowException, NotSupportedMethodException {
    if (comment != null && !"".equals(comment)) {
      properties.setDiscardComment(comment);
    }
    WORKFLOW_MANAGER.prepareWithdraw(properties);
  }



  /**
   * True if at least one {@link Item} is locked by another {@link User}
   *
   * @param uris
   * @param user
   * @return
   */
  protected boolean hasImageLocked(List<String> uris, User user) {
    for (String uri : uris) {
      if (Locks.isLocked(uri.toString(), user.getEmail())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Update logo of {@link Container}
   *
   * @param Container
   * @param f
   * @param user
   * @return
   * @throws ImejiException
   * @throws URISyntaxException
   */
  protected Container setLogo(Container container, File f)
      throws ImejiException, IOException, URISyntaxException {
    InternalStorageManager ism = new InternalStorageManager();
    if (f != null) {
      String url = ism.generateUrl(container.getIdString(), f.getName(), FileResolution.THUMBNAIL);
      container.setLogoUrl(URI.create(url));
      ism.changeThumbnail(f, url);
    } else {
      ism.removeFile(container.getLogoUrl().toString());
      container.setLogoUrl(null);
    }
    return container;
  }

  public static int getMin(int a, int b) {
    if (a < b) {
      return a;
    }
    return b;
  }

  /**
   * Get the instance default instance
   * 
   * @return
   */
  public License getDefaultLicense() {
    ImejiLicenses lic = StringHelper.isNullOrEmptyTrim(Imeji.CONFIG.getDefaultLicense())
        ? ImejiLicenses.CC0 : ImejiLicenses.valueOf(Imeji.CONFIG.getDefaultLicense());
    License license = new License();
    license.setName(lic.name());
    license.setLabel(lic.getLabel());
    license.setUrl(lic.getUrl());
    return license;
  }
}
