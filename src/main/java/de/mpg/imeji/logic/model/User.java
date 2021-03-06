package de.mpg.imeji.logic.model;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnum;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.mpg.imeji.j2j.annotations.j2jId;
import de.mpg.imeji.j2j.annotations.j2jList;
import de.mpg.imeji.j2j.annotations.j2jLiteral;
import de.mpg.imeji.j2j.annotations.j2jModel;
import de.mpg.imeji.j2j.annotations.j2jResource;
import de.mpg.imeji.logic.ImejiNamespaces;
import de.mpg.imeji.logic.model.aspects.AccessMember;
import de.mpg.imeji.logic.model.aspects.ChangeMember;
import de.mpg.imeji.logic.model.aspects.CloneURI;
import de.mpg.imeji.logic.model.aspects.ResourceLastModified;
import de.mpg.imeji.logic.util.IdentifierUtil;
import de.mpg.imeji.logic.util.URIListHelper;

/**
 * imeji user
 *
 * @author saquet (initial creation)
 * @author $Author$ (last modification)
 * @version $Revision$ $LastChangedDate$
 */
@j2jResource("http://imeji.org/terms/user")
@j2jModel("user")
@j2jId(getMethod = "getId", setMethod = "setId")
public class User implements Serializable, ResourceLastModified, CloneURI, AccessMember {
  private static final long serialVersionUID = -8961821901552709120L;
  @j2jLiteral("http://xmlns.com/foaf/0.1/email")
  private String email;
  @j2jLiteral("http://xmlns.com/foaf/0.1/password")
  private String encryptedPassword;
  @j2jLiteral("http://xmlns.com/foaf/0.1/person")
  private Person person = new Person();
  @j2jList("http://imeji.org/terms/grant")
  private List<String> grants = new ArrayList<String>();
  @j2jLiteral("http://imeji.org/terms/quota")
  private long quota = -1;
  @j2jLiteral("http://imeji.org/terms/apiKey")
  private String apiKey;
  private URI id = IdentifierUtil.newURI(User.class);
  private List<UserGroup> groups = new ArrayList<>();

  // User properties for registration
  @j2jLiteral(ImejiNamespaces.DATE_CREATED)
  private Calendar created;

  // User properties for registration
  @j2jLiteral(ImejiNamespaces.LAST_MODIFICATION_DATE)
  private Calendar modified;

  @j2jResource(ImejiNamespaces.USER_STATUS)
  private URI userStatus = URI.create(UserStatus.ACTIVE.getUriString());

  @j2jLiteral("http://imeji.org/terms/registrationToken")
  private String registrationToken;


  private static final Logger LOGGER = LogManager.getLogger(User.class);

  @Override
  public Object cloneURI() {
    User newUser = new User();
    newUser.setId(this.getId());
    return newUser;
  }

  @Override
  public void accessMember(ChangeMember changeMember) {

    // access member:
    // (a) add/remove/delete grant
    try {
      Field grantsField = User.class.getDeclaredField("grants");
      if (changeMember.getField().equals(grantsField) && changeMember.getValue() instanceof Grant) {
        String grantString = ((Grant) changeMember.getValue()).toGrantString();
        int indexOfExistingGrant = Grant.getGrantPositionInGrantsList(this.grants, grantString);
        URIListHelper.addEditRemoveElementOfURIList(this.grants, changeMember.getAction(), grantString, indexOfExistingGrant);
      } else {
        LOGGER.info("Could not set member in User");
      }
    } catch (NoSuchFieldException | SecurityException e) {
      LOGGER.error("Could not set member in User", e);
    }
  }


  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setEncryptedPassword(String encryptedPassword) {
    this.encryptedPassword = encryptedPassword;
  }

  public String getEncryptedPassword() {
    return encryptedPassword;
  }

  public void setGrants(Collection<String> grants) {
    this.grants = (List<String>) grants;
  }

  public Collection<String> getGrants() {
    return grants;
  }

  public void setId(URI id) {
    this.id = id;
  }

  public URI getId() {
    return id;
  }

  /**
   * @return the groups
   */
  public List<UserGroup> getGroups() {
    return groups;
  }

  /**
   * @param groups the groups to set
   */
  public void setGroups(List<UserGroup> groups) {
    this.groups = groups;
  }

  /**
   * @return the person
   */
  public Person getPerson() {
    return person;
  }

  /**
   * @param person the person to set
   */
  public void setPerson(Person person) {
    this.person = person;
  }

  /**
   *
   * @return
   */
  public long getQuota() {
    return quota;
  }

  /**
   *
   * @param quota
   */
  public void setQuota(long quota) {
    this.quota = quota;
  }

  @XmlEnum(String.class)
  public enum UserStatus {
    ACTIVE(new String(ImejiNamespaces.USER_STATUS + "#ACTIVE")),
    INACTIVE(new String(ImejiNamespaces.USER_STATUS + "#INACTIVE")),
    INVITED(ImejiNamespaces.USER_STATUS + "#INVITED"),
    REMOVED(new String(ImejiNamespaces.USER_STATUS + "#REMOVED"));

    private final String uri;

    private UserStatus(String uri) {
      this.uri = uri;
    }

    public String getUriString() {
      return uri;
    }

    public URI getURI() {
      return URI.create(uri);
    }
  }

  @XmlElement(name = "created", namespace = "http://purl.org/dc/terms/")
  public Calendar getCreated() {
    return created;
  }

  public void setCreated(Calendar created) {
    this.created = created;
  }

  public void setUserStatus(UserStatus status) {
    this.userStatus = URI.create(status.getUriString());
  }

  @XmlElement(name = "userStatus", namespace = "http://imeji.org/terms/")
  public UserStatus getUserStatus() {
    return UserStatus.valueOf(userStatus.getFragment());
  }

  @XmlElement(name = "registrationToken", namespace = "http://imeji.org/terms/")
  public String getRegistrationToken() {
    return registrationToken;
  }

  public void setRegistrationToken(String token) {
    this.registrationToken = token;
  }

  public boolean isActive() {
    return userStatus.equals(UserStatus.ACTIVE.getURI());
  }

  public boolean isRemoved() {
    return userStatus.equals(UserStatus.REMOVED.getURI());
  }

  public Calendar getModified() {
    return modified;
  }

  public void setModified(Calendar modified) {
    this.modified = modified;
  }

  @Override
  public Field getTimeStampField() {
    try {
      return User.class.getDeclaredField("modified");
    } catch (NoSuchFieldException | SecurityException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }



}
