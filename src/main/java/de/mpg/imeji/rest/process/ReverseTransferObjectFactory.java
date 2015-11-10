package de.mpg.imeji.rest.process;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

import de.mpg.imeji.exceptions.BadRequestException;
import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.controller.ProfileController;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.Album;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.ContainerMetadata;
import de.mpg.imeji.logic.vo.Item;
import de.mpg.imeji.logic.vo.Metadata;
import de.mpg.imeji.logic.vo.MetadataProfile;
import de.mpg.imeji.logic.vo.Organization;
import de.mpg.imeji.logic.vo.Person;
import de.mpg.imeji.logic.vo.Statement;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.logic.vo.predefinedMetadata.ConePerson;
import de.mpg.imeji.logic.vo.predefinedMetadata.Geolocation;
import de.mpg.imeji.logic.vo.predefinedMetadata.License;
import de.mpg.imeji.logic.vo.predefinedMetadata.Link;
import de.mpg.imeji.logic.vo.predefinedMetadata.Number;
import de.mpg.imeji.logic.vo.predefinedMetadata.Publication;
import de.mpg.imeji.logic.vo.predefinedMetadata.Text;
import de.mpg.imeji.rest.defaultTO.DefaultItemTO;
import de.mpg.imeji.rest.helper.ItemTransferHelper;
import de.mpg.imeji.rest.helper.MetadataTransferHelper;
import de.mpg.imeji.rest.helper.ProfileTransferHelper;
import de.mpg.imeji.rest.to.AlbumTO;
import de.mpg.imeji.rest.to.CollectionTO;
import de.mpg.imeji.rest.to.IdentifierTO;
import de.mpg.imeji.rest.to.ItemTO;
import de.mpg.imeji.rest.to.LiteralConstraintTO;
import de.mpg.imeji.rest.to.MetadataProfileTO;
import de.mpg.imeji.rest.to.MetadataSetTO;
import de.mpg.imeji.rest.to.OrganizationTO;
import de.mpg.imeji.rest.to.PersonTO;
import de.mpg.imeji.rest.to.StatementTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.ConePersonTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.DateTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.GeolocationTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.LicenseTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.LinkTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.NumberTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.PublicationTO;
import de.mpg.imeji.rest.to.predefinedMetadataTO.TextTO;

public class ReverseTransferObjectFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReverseTransferObjectFactory.class);

  public enum TRANSFER_MODE {
    CREATE, UPDATE
  }

  public static void transferCollection(CollectionTO to, CollectionImeji vo, TRANSFER_MODE mode,
      User u) {

    ContainerMetadata metadata = new ContainerMetadata();

    metadata.setTitle(to.getTitle());
    metadata.setDescription(to.getDescription());

    // set contributors
    transferCollectionContributors(to.getContributors(), metadata, u, mode);
    vo.setMetadata(metadata);

  }

  public static void transferAlbum(AlbumTO to, Album vo, TRANSFER_MODE mode, User u) {
    ContainerMetadata metadata = new ContainerMetadata();
    metadata.setTitle(to.getTitle());
    metadata.setDescription(to.getDescription());

    // set contributors
    transferCollectionContributors(to.getContributors(), metadata, u, mode);
    vo.setMetadata(metadata);
  }

  /**
   * Transfert an {@link ItemTO} into an Item
   * 
   * @param to
   * @param vo
   * @param u
   * @param mode
   * @throws ImejiException
   */
  public static void transferItem(ItemTO to, Item vo, User u, TRANSFER_MODE mode)
      throws ImejiException {

    // only fields which can be transferred for TO to VO!!!
    if (mode == TRANSFER_MODE.CREATE) {
      if (!isNullOrEmpty(to.getId()))
        vo.setId(ObjectHelper.getURI(Item.class, to.getId()));

      if (!isNullOrEmpty(to.getCollectionId()))
        vo.setCollection(ObjectHelper.getURI(CollectionImeji.class, to.getCollectionId()));
    }

    if (!isNullOrEmpty(to.getFilename()))
      vo.setFilename(to.getFilename());

    transferItemMetadata(to, vo, u, mode);
  }

  public static void transferItemMetadata(ItemTO to, Item vo, User u, TRANSFER_MODE mode)
      throws ImejiException {

    Collection<Metadata> voMDs = vo.getMetadataSet().getMetadata();
    // Collection<Metadata> copyOfvoMDs = ImmutableList.copyOf(voMDs);
    voMDs.clear();

    MetadataProfile mp = getMetadataProfile(vo.getCollection(), u);

    validateMetadata(to, mp);

    for (Statement st : mp.getStatements()) {
      final URI stURI = st.getId();

      Collection<MetadataSetTO> mdsList = lookUpMetadata(to, st);
      // Metadata mdVOPrev = lookUpMetadata(copyOfvoMDs, stURI,
      // st.getType());
      if (mdsList.isEmpty()) {
        {
          final String message =
              "Statement { type: \"" + st.getType() + "\", id: \"" + stURI
                  + "\" } has not been found for item id: \"" + vo.getId() + "\"";
          // throw new RuntimeException(message);
          LOGGER.debug(message);
        }
      } else if (mdsList.size() > 1 && "1".equals(st.getMaxOccurs())) {
        throw new BadRequestException("Statement { type: \"" + st.getType() + "\", id: \"" + stURI
            + "\" } for item id: \"" + vo.getId() + "\" occurs more then once");
      } else
        for (MetadataSetTO md : mdsList)
          switch (st.getType().toString()) {
            case "http://imeji.org/terms/metadata#text":
              TextTO textTO = (TextTO) md.getValue();
              if (!isNullOrEmpty(textTO.getText())) {
                Text mdVO = new Text();
                mdVO.setStatement(stURI);
                mdVO.setText(textTO.getText());
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#geolocation":
              GeolocationTO geoTO = (GeolocationTO) md.getValue();
              if (geoTO != null) {
                Geolocation mdVO = new Geolocation();
                mdVO.setStatement(stURI);
                mdVO.setName(geoTO.getName());
                mdVO.setLatitude(geoTO.getLatitude());
                mdVO.setLongitude(geoTO.getLongitude());
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#number":
              NumberTO numberTO = (NumberTO) md.getValue();
              if (numberTO != null) {
                Number mdVO = new Number();
                mdVO.setStatement(stURI);
                mdVO.setNumber(numberTO.getNumber());
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#conePerson":
              ConePersonTO personTO = (ConePersonTO) md.getValue();
              if (personTO != null) {
                ConePerson mdVO = new ConePerson();
                mdVO.setStatement(stURI);
                mdVO.setPerson(new Person());
                transferPerson(personTO.getPerson(), mdVO.getPerson(), mode);
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#date":
              DateTO dateTO = (DateTO) md.getValue();
              if (!isNullOrEmpty(dateTO.getDate())) {
                de.mpg.imeji.logic.vo.predefinedMetadata.Date mdVO =
                    new de.mpg.imeji.logic.vo.predefinedMetadata.Date();
                mdVO.setStatement(stURI);
                mdVO.setDate(dateTO.getDate());
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#license":
              LicenseTO licenseTO = (LicenseTO) md.getValue();
              final String lic = licenseTO.getLicense();
              final String url = licenseTO.getUrl();
              if (!isNullOrEmpty(lic) || !isNullOrEmpty(url)) {
                License mdVO = new License();
                mdVO.setStatement(stURI);
                // set license to uri if empty
                mdVO.setLicense(isNullOrEmpty(lic) ? url : lic);
                if (!isNullOrEmpty(url))
                  mdVO.setExternalUri(URI.create(url));
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#publication":
              PublicationTO pubTO = (PublicationTO) md.getValue();
              if (!isNullOrEmpty(pubTO.getPublication())) {
                Publication mdVO = new Publication();
                mdVO.setStatement(stURI);
                mdVO.setUri(URI.create(pubTO.getPublication()));
                mdVO.setExportFormat(pubTO.getFormat());
                mdVO.setCitation(pubTO.getCitation());
                voMDs.add(mdVO);
              }
              break;
            case "http://imeji.org/terms/metadata#link":
              LinkTO linkTO = (LinkTO) md.getValue();
              if (!isNullOrEmpty(linkTO.getUrl())) {
                Link mdVO = new Link();
                mdVO.setStatement(stURI);
                mdVO.setLabel(linkTO.getLink());
                mdVO.setUri(URI.create(linkTO.getUrl()));
                voMDs.add(mdVO);
              }
              break;
          }
    }
  }

  /**
   * Transfer a {@link MetadataProfileTO} into a {@link MetadataProfile}
   * 
   * @param to
   * @param vo
   * @param mode
   */
  public static void transferMetadataProfile(MetadataProfileTO to, MetadataProfile vo,
      TRANSFER_MODE mode) {
    if (mode == TRANSFER_MODE.CREATE) {
      vo.setTitle(to.getTitle());
      vo.setDescription(to.getDescription());
      vo.setDefault(to.getDefault());
      for (StatementTO stTO : to.getStatements()) {
        Statement stVO = new Statement();
        stVO.setType(stTO.getType());
        stVO.setLabels(stTO.getLabels());
        stVO.setVocabulary(stTO.getVocabulary());
        for (LiteralConstraintTO lc : stTO.getLiteralConstraints()) {
          stVO.getLiteralConstraints().add(lc.getValue());
        }
        stVO.setMinOccurs(stTO.getMinOccurs());
        stVO.setMaxOccurs(stTO.getMaxOccurs());
        // TODO: check namespace
        // stVO.setNamespace(???);
        if (!isNullOrEmpty(stTO.getParentStatementId()))
          stVO.setParent(URI.create(stTO.getParentStatementId()));
        vo.getStatements().add(stVO);
      }
    }

  }

  /**
   * Check all item metadata statement/types: they should be presented in the MetadataProfile
   * statements
   * 
   * @param to
   * @param mp
   * @throws de.mpg.imeji.exceptions.BadRequestException
   */
  private static void validateMetadata(ItemTO to, MetadataProfile mp) throws BadRequestException {
    for (MetadataSetTO md : to.getMetadata()) {
      try {
        lookUpStatement(mp.getStatements(), md.getTypeUri(), md.getStatementUri());
      } catch (NoSuchElementException e) {
        throw new BadRequestException("Cannot find { typeUri: " + md.getTypeUri()
            + " , statementUri: " + md.getStatementUri() + "} in profile: " + mp.getId());
      }
    }
  }

  private static MetadataProfile getMetadataProfile(URI collectionURI, User u)
      throws ImejiException {
    ProfileController pc = new ProfileController();
    return pc.retrieveByCollectionId(collectionURI, u);
  }

  private static Statement lookUpStatement(Collection<Statement> statements, final URI type,
      final URI statementUri) {
    return Iterables.find(statements, new Predicate<Statement>() {
      @Override
      public boolean apply(Statement st) {
        return st.getType().equals(type) && st.getId().equals(statementUri);
      }
    });
  }

  private static Collection<MetadataSetTO> lookUpMetadata(ItemTO to, final Statement st) {
    return Collections2.filter(to.getMetadata(), new Predicate<MetadataSetTO>() {
      @Override
      public boolean apply(MetadataSetTO md) {
        return md.getTypeUri().equals(st.getType()) && md.getStatementUri().equals(st.getId());
      }
    });
  }

  /**
   * Transfer a {@link PersonTO} into a {@link Person}
   * 
   * @param pto
   * @param p
   * @param mode
   */
  public static void transferPerson(PersonTO pto, Person p, TRANSFER_MODE mode) {

    if (mode == TRANSFER_MODE.CREATE) {
      // p.setPos(pto.getPosition());
      IdentifierTO ito = new IdentifierTO();
      ito.setValue(pto.getIdentifiers().isEmpty() ? null : pto.getIdentifiers().get(0).getValue());
      p.setIdentifier(ito.getValue());
    }
    p.setRole(URI.create(pto.getRole()));
    p.setFamilyName(pto.getFamilyName());
    p.setGivenName(pto.getGivenName());
    p.setCompleteName(pto.getCompleteName());
    p.setAlternativeName(pto.getAlternativeName());

    // set oganizations
    transferContributorOrganizations(pto.getOrganizations(), p, mode);

  }


  public static void transferCollectionContributors(List<PersonTO> persons,
      ContainerMetadata metadata, User u, TRANSFER_MODE mode) {
    for (PersonTO pTO : persons) {
      Person person = new Person();
      person.setFamilyName(pTO.getFamilyName());
      person.setGivenName(pTO.getGivenName());
      person.setCompleteName(pTO.getCompleteName());
      person.setAlternativeName(pTO.getAlternativeName());
      person.setRole(URI.create(pTO.getRole()));
      // person.setPos(pTO.getPosition());

      if (pTO.getIdentifiers().size() == 1) {
        // set the identifier of current person
        IdentifierTO ito = new IdentifierTO();
        ito.setValue(pTO.getIdentifiers().get(0).getValue());
        person.setIdentifier(ito.getValue());
      } else if (pTO.getIdentifiers().size() > 1) {
        System.out.println("I have more identifiers than needed for Person");
      }

      // set organizations
      transferContributorOrganizations(pTO.getOrganizations(), person, mode);
      metadata.getPersons().add(person);
    }

    if (metadata.getPersons().size() == 0 && TRANSFER_MODE.CREATE.equals(mode)) {
      Person personU = new Person();
      PersonTO pTo = new PersonTO();
      personU.setFamilyName(u.getPerson().getFamilyName());
      personU.setGivenName(u.getPerson().getGivenName());
      personU.setCompleteName(u.getPerson().getCompleteName());
      personU.setAlternativeName(u.getPerson().getAlternativeName());
      if (!isNullOrEmpty(u.getPerson().getIdentifier())) {
        IdentifierTO ito = new IdentifierTO();
        ito.setValue(u.getPerson().getIdentifier());
        personU.setIdentifier(ito.getValue());
      }
      personU.setOrganizations(u.getPerson().getOrganizations());
      personU.setRole(URI.create(pTo.getRole()));
      metadata.getPersons().add(personU);
    }

  }

  public static void transferContributorOrganizations(List<OrganizationTO> orgs, Person person,
      TRANSFER_MODE mode) {
    for (OrganizationTO orgTO : orgs) {
      Organization org = new Organization();

      if (mode == TRANSFER_MODE.CREATE) {
        // TODO: Organization can have only one identifier, why
        // OrganizationTO has many?
        // get only first one!
        if (orgTO.getIdentifiers().size() > 0) {
          IdentifierTO ito = new IdentifierTO();
          ito.setValue(orgTO.getIdentifiers().get(0).getValue());
          org.setIdentifier(ito.getValue());
          if (orgTO.getIdentifiers().size() > 1) {
            LOGGER.info("Have more organization identifiers than needed");
          }
        }
      }

      org.setName(orgTO.getName());
      org.setDescription(orgTO.getDescription());
      org.setCity(orgTO.getCity());
      org.setCountry(orgTO.getCountry());
      person.getOrganizations().add(org);
    }
  }

  public static void sort(List<ItemTransferHelper> list, ItemTransferHelper newItem) {
    if (list.size() == 0)
      list.add(0, newItem);
    else {
      int i = 0;
      for (ItemTransferHelper item : list) {
        i++;
        if (newItem.getStatementID().equals(item.getStatementID())) {
          if (newItem.getPos() < item.getPos()) {
            i--;
            break;
          }
        }
      }
      list.add(i, newItem);
    }
  }



  /**
   * Transfer a {@link DefaultItemTO} into an {@link ItemTO}
   * 
   * @param profileTO
   * @param defaultTO
   * @param itemTO
   * @param updatedAll
   * @throws BadRequestException
   * @throws JsonParseException
   * @throws JsonMappingException
   */

  public static void transferDefaultItemTOtoItemTO(MetadataProfileTO profileTO,
      DefaultItemTO defaultTO, ItemTO itemTO, boolean updatedAll) throws BadRequestException,
      JsonParseException, JsonMappingException {
    if (defaultTO.getMetadata() == null) {
      itemTO.getMetadata().clear();
    } else {
      // NEW
      for (String label : defaultTO.getMetadata().keySet()) {
        // Get statement according to the label
        StatementTO statement = ProfileTransferHelper.findStatementByLabel(label, profileTO);
        // Get the new metadata according to the json and the statement
        List<MetadataSetTO> newMetadata =
            MetadataTransferHelper.parseMetadata(defaultTO.getMetadata().get(label), statement);
        // add/replace the metadata to the itemto
        if (!newMetadata.isEmpty()) {
          // remove all the metadata with the same statement
          itemTO.clearMetadata(newMetadata.get(0).getStatementUri());
          // add the new metadata
          itemTO.getMetadata().addAll(newMetadata);
        }
      }

    }
  }
}
