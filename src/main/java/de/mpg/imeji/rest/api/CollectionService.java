package de.mpg.imeji.rest.api;

import de.mpg.imeji.logic.auth.exception.NotAllowedError;
import de.mpg.imeji.logic.auth.exception.UnprocessableError;
import de.mpg.imeji.logic.controller.CollectionController;
import de.mpg.imeji.logic.controller.ProfileController;
import de.mpg.imeji.logic.controller.exceptions.NotFoundError;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.CollectionImeji;
import de.mpg.imeji.logic.vo.MetadataProfile;
import de.mpg.imeji.logic.vo.User;
import de.mpg.imeji.presentation.util.ImejiFactory;
import de.mpg.imeji.rest.process.CommonUtils;
import de.mpg.imeji.rest.process.TransferObjectFactory;
import de.mpg.imeji.rest.to.CollectionTO;
import de.mpg.j2j.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotSupportedException;
import java.net.URI;
import java.util.List;
import java.util.prefs.BackingStoreException;

import static de.mpg.imeji.rest.process.ReverseTransferObjectFactory.TRANSFER_MODE.CREATE;
import static de.mpg.imeji.rest.process.ReverseTransferObjectFactory.transferCollection;

public class CollectionService implements API<CollectionTO> {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(CollectionService.class);
	
	private CollectionTO getCollectionTO (CollectionController controller, String id, User u) throws Exception {
		CollectionTO to = new CollectionTO();
		CollectionImeji vo = controller.retrieve(
				ObjectHelper.getURI(CollectionImeji.class, id), u);
		TransferObjectFactory.transferCollection(vo, to);
		return to;
	}

	@Override
	public CollectionTO read(String id, User u) throws Exception {
		CollectionController controller = new CollectionController();
		return getCollectionTO(controller, id, u);
	}

	@Override
	public CollectionTO create(CollectionTO to, User u) throws Exception {
		CollectionController cc = new CollectionController();
		ProfileController pc = new ProfileController();

		MetadataProfile mp = null;
		String profileId = to.getProfile().getProfileId();
		String method = to.getProfile().getMethod();
		String newId = null;
		// create new profile (take default)
		if (profileId == null || "".equals(profileId))
			mp = pc.create(ImejiFactory.newProfile(), u);
		// set reference to existed profile
		else if (profileId != null && "reference".equalsIgnoreCase(method))
			try {
				mp = pc.retrieve(
					ObjectHelper.getURI(MetadataProfile.class, profileId), u);
			}
			catch (Exception e)
			{
				throw new UnprocessableError("Can not find the metadata profile you have referenced in the JSON body");
				
			}
		// copy existed profile
		else if (profileId != null && "copy".equalsIgnoreCase(method)) {
			try {
			mp = pc.retrieve(
					ObjectHelper.getURI(MetadataProfile.class, profileId), u);
			}
			catch (Exception e)
			{
				throw new UnprocessableError("Can not find the metadata profile you want to copy from in the JSON body");
			}
			mp = pc.create(mp.clone(), u);
			pc.update(mp, u);
		} else {
			// throw exception if no method specified
			final String msg = "Bad metadata profile method definition:"
					+ method;
			LOGGER.error(msg);
			throw new BadRequestException(msg);
		}
		CollectionImeji vo = new CollectionImeji();
		transferCollection(to, vo, CREATE);
//		try {
			URI collectionURI = cc.create(vo, mp, u);
			return read(CommonUtils.extractIDFromURI(collectionURI), u);
//		} catch (Exception e) {
//			LOGGER.error("Cannot create collection:");
//			e.printStackTrace();
//			return null;
//		}
	}

	@Override
	public CollectionTO release(String id, User u) throws Exception {

		CollectionController controller = new CollectionController();
		CollectionImeji vo = controller.retrieve(
				ObjectHelper.getURI(CollectionImeji.class, id), u);
		controller.release(vo, u);

		//Now Read the collection and return it back
		return getCollectionTO(controller, id, u);

	}

	@Override
	public CollectionTO update(CollectionTO o, User u)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean delete(String id, User u) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public CollectionTO withdraw(String id, User u, String discardComment) throws Exception {

		CollectionController controller = new CollectionController();
		CollectionImeji vo = controller.retrieve(
				ObjectHelper.getURI(CollectionImeji.class, id), u);
		vo.setDiscardComment(discardComment);
		controller.withdraw(vo, u);

		//Now Read the withdrawn collection and return it back
		return getCollectionTO(controller, id, u);
	}

	@Override
	public void share(String id, String userId, List<String> roles, User u)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void unshare(String id, String userId, List<String> roles, User u)
			throws	Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> search(String q, User u) throws NotSupportedException,
			Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
