package de.mpg.imeji.logic.security.user;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.j2j.helper.J2JHelper;
import de.mpg.imeji.logic.config.Imeji;
import de.mpg.imeji.logic.db.reader.ReaderFacade;
import de.mpg.imeji.logic.db.writer.WriterFacade;
import de.mpg.imeji.logic.generic.AccessElement;
import de.mpg.imeji.logic.generic.ImejiControllerAbstract;
import de.mpg.imeji.logic.model.Item;
import de.mpg.imeji.logic.model.User;
import de.mpg.imeji.logic.model.UserGroup;
import de.mpg.imeji.logic.model.aspects.ChangeMember;
import de.mpg.imeji.logic.model.aspects.ChangeMember.ActionType;
import de.mpg.imeji.logic.search.jenasearch.ImejiSPARQL;
import de.mpg.imeji.logic.search.jenasearch.JenaCustomQueries;
import de.mpg.imeji.logic.security.usergroup.UserGroupService;
import de.mpg.imeji.util.DateHelper;

/**
 * Resource Controllers for {@link User}
 *
 * @author saquet
 *
 */
class UserController extends ImejiControllerAbstract<User> implements AccessElement<User> {
  private static final ReaderFacade READER = new ReaderFacade(Imeji.userModel);
  private static final WriterFacade WRITER = new WriterFacade(Imeji.userModel);

  private static final Logger LOGGER = LogManager.getLogger(UserController.class);
  private static final Comparator<User> USER_COMPARATOR_BY_NAME = new Comparator<User>() {
    @Override
    public int compare(User c1, User c2) {
      try {
        return c1.getPerson().getCompleteName().toLowerCase().compareTo(c2.getPerson().getCompleteName().toLowerCase());
      } catch (final Exception e) {
        LOGGER.error("Error comparing user " + c1.getEmail() + " with user " + c2.getEmail(), e);
        return c1.getEmail().compareTo(c2.getEmail());
      }
    }
  };

  @Override
  public List<User> createBatch(List<User> l, User user) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<User> retrieveBatch(List<String> ids, User user) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<User> retrieveBatchLazy(List<String> ids, User user) throws ImejiException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<User> updateBatch(List<User> usersToUpdate, User user) throws ImejiException {
    List<User> updatedUsers = this.fromObjectList(WRITER.update(J2JHelper.cast2ObjectList(usersToUpdate), Imeji.adminUser, true));
    return updatedUsers;
  }

  @Override
  public void deleteBatch(List<User> l, User user) throws ImejiException {
    // TODO Auto-generated method stub

  }

  public User create(User user) throws ImejiException {
    final Calendar now = DateHelper.getCurrentDate();
    user.setCreated(now);
    List<User> createdUsers = this.fromObjectList(WRITER.create(WriterFacade.toList(user), Imeji.adminUser));
    if (!createdUsers.isEmpty()) {
      return createdUsers.get(0);
    }
    return user;
  }

  /**
   * Retrieve a {@link User} according to its uri (id)
   *
   * @param uri
   * @return
   * @throws ImejiException
   */
  public User retrieve(URI uri, User user) throws ImejiException {
    final User u = (User) READER.read(uri.toString(), user, new User());
    if (u.isActive()) {
      final UserGroupService ugc = new UserGroupService();
      u.setGroups((List<UserGroup>) ugc.searchByUser(u, Imeji.adminUser));
    }
    return u;
  }

  /**
   * Retrieve all {@link Item} (all status, all users) in imeji
   *
   * @return
   * @throws ImejiException
   */
  public List<User> retrieveAll() throws ImejiException {
    final List<String> uris = ImejiSPARQL.exec(JenaCustomQueries.selectUserAll(), Imeji.userModel);
    final List<User> users = new ArrayList<>();
    for (final String uri : uris) {
      users.add(retrieve(URI.create(uri), Imeji.adminUser));
    }
    return users;
  }

  /**
   * Load all {@link User}
   *
   * @param uris
   * @return
   * @throws ImejiException
   */
  public List<User> retrieveBatchLazy(List<String> uris, int limit) {
    final int max = limit < uris.size() && limit > 0 ? limit : uris.size();
    final List<User> users = new ArrayList<User>(max);
    for (int i = 0; i < max; i++) {
      try {
        users.add((User) READER.readLazy(uris.get(i), Imeji.adminUser, new User()));
      } catch (final ImejiException e) {
        LOGGER.error("Error reading user", e);
      }
    }
    Collections.sort(users, USER_COMPARATOR_BY_NAME);
    return users;
  }

  /**
   * Load all {@link User}
   *
   * @param uris
   * @return
   * @throws ImejiException
   */
  public Collection<User> retrieveBatch(List<String> uris, int limit) {
    final int max = limit < uris.size() && limit > 0 ? limit : uris.size();
    final List<User> users = new ArrayList<User>(max);
    for (int i = 0; i < max; i++) {
      try {
        users.add((User) READER.read(uris.get(i), Imeji.adminUser, new User()));
      } catch (final ImejiException e) {
        LOGGER.error("Error reading user", e);
      }
    }
    Collections.sort(users, USER_COMPARATOR_BY_NAME);
    return users;
  }

  /**
   * Delete a {@link User}
   *
   * @param user
   * @throws ImejiException
   */
  public void delete(User user) throws ImejiException {
    // remove User from User Groups
    final UserGroupService ugc = new UserGroupService();
    ugc.removeUserFromAllGroups(user, Imeji.adminUser);
    // remove user
    WRITER.delete(WriterFacade.toList(user), Imeji.adminUser);
  }


  @Override
  public List<User> fromObjectList(List<?> objectList) {
    List<User> userList = new ArrayList<User>(0);
    if (!objectList.isEmpty()) {
      if (objectList.get(0) instanceof User) {
        userList = (List<User>) objectList;
      }
    }
    return userList;
  }

  @Override
  public User changeElement(User user, User imejiDataObject, Field elementField, ActionType action, Object element) throws ImejiException {

    ChangeMember changeMember = new ChangeMember(action, imejiDataObject, elementField, element);
    User updatedUser = (User) WRITER.changeElement(changeMember, user);
    return updatedUser;
  }



}
