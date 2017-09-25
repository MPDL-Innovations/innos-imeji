package de.mpg.imeji.logic.batch;

import java.util.concurrent.Callable;

import de.mpg.imeji.exceptions.ImejiException;
import de.mpg.imeji.logic.messaging.aggregation.DailyUploadOrUpdateFileMessageAggregation;

/**
 * For each collection that has changed, send email to subscribed user with changes. (Only one email
 * per user)
 * 
 * @author jandura
 *
 */
public class AggregateMessages implements Callable<Integer> {

  @Override
  public Integer call() throws ImejiException {
    DailyUploadOrUpdateFileMessageAggregation aggregation =
        new DailyUploadOrUpdateFileMessageAggregation();
    aggregation.aggregate();
    aggregation.postProcessing();
    return 1;
  }

}